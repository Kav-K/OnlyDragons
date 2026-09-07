package com.kaveenk.onlydragons.application.proc;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.CombatEncounter;
import com.kaveenk.onlydragons.domain.enchant.*;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One encounter, one owning server thread, one tick-driven bounded queue. No listeners/tasks.
 * The composition root captures Session at launch, settles physical cancellation, calls tick
 * once per server tick, and clears sessions on quit/death/exit. Reset/disable closes and discards
 * this coordinator and its encounter. Never feed tempo back into a captured stat snapshot.
 */
public final class ProcCoordinator implements AutoCloseable {
    /**
     * Captured lifecycle identity used to prevent old arrows/callbacks rebuilding a new login's buffs.
     * @param ownerId nonnull player UUID
     * @param token nonnull fresh session UUID allocated by the composition root
     */
    public record Session(UUID ownerId, UUID token) {
        /**
         * Rejects null owner/token; freshness is the caller's lifecycle responsibility.
         */
        public Session { Objects.requireNonNull(ownerId); Objects.requireNonNull(token); }
    }
    /**
     * Positive independent bounds; queue overflow rejects a whole child group after parent credit.
     * @param queueCapacity maximum pending children in this encounter
     * @param maxDuePerTick maximum polled children per distinct drain tick, including failures/inactive entries
     * @param maxSessions maximum simultaneously activated owners
     * @param spacingTicks positive game ticks between siblings
     */
    public record Limits(int queueCapacity, int maxDuePerTick, int maxSessions, int spacingTicks) {
        /**
         * Rejects any nonpositive bound; limits are immutable for this coordinator.
         */
        public Limits {
            if (queueCapacity < 1 || maxDuePerTick < 1 || maxSessions < 1 || spacingTicks < 1) {
                throw new IllegalArgumentException("Queue/session limits and spacing must be positive");
            }
        }
    }
    /**
     * Child admission outcome separate from physical acceptance. CAPACITY_REJECTED and
     * INACTIVE_SESSION preserve already-committed parent damage; NO_CHILDREN can still build Tempo.
     */
    public enum Admission {
        /** The entire rolled child group was admitted. */ QUEUED,
        /** The accepted physical impact rolled zero children. */ NO_CHILDREN,
        /** The whole child group was refused by queue capacity. */ CAPACITY_REJECTED,
        /** The captured session is not currently active; no new children are admitted. */ INACTIVE_SESSION,
        /** The encounter rejected the parent, so no children are requested. */ PHYSICAL_REJECTED
    }
    /**
     * Returned physical commit plus child admission, not a request to reapply damage.
     * @param damage authoritative encounter result
     * @param admission reason children were or were not queued
     * @param requestedChildren rolled count when accepted; zero for rejected physical input
     * @param children immutable copy of the admitted whole group, empty when none admitted
     */
    public record PhysicalResult(DamageResult damage, Admission admission, int requestedChildren,
                                 List<ProcCommand> children) {
        /**
         * Copies children; this diagnostic DTO does not independently validate admission/count consistency.
         */
        public PhysicalResult { children = List.copyOf(children); }
    }
    /**
     * Immutable inspection without advancing time or expiring state.
     * @param queued current pending child count
     * @param sessions current activated owner count
     * @param tempoStates retained buff count, expired when the clock next advances
     * @param capacityRejectedChildren cumulative children rejected by whole-group capacity
     * @param inactiveRejectedChildren cumulative children denied or discarded for stale sessions
     * @param clearedChildren cumulative children explicitly removed by session cleanup or close
     */
    public record Metrics(int queued, int sessions, int tempoStates, long capacityRejectedChildren,
                          long inactiveRejectedChildren, long clearedChildren) {}
    /**
     * One consumed child whose evaluation threw a validation/arithmetic exception.
     * @param command exact failed command, never automatically requeued
     * @param reason exception message; may be null for an injected exception without a message
     */
    public record ChildFailure(ProcCommand command, String reason) {}
    /**
     * Partial-outcome report for a consumed tick; successful earlier commits are never rolled back.
     * @param results copied committed/rejected child results in drain order
     * @param failures copied failed commands in encounter order; no result is fabricated for them
     */
    public record Drain(List<DamageResult> results, List<ChildFailure> failures) {
        /**
         * Freezes both lists; callers reconcile successes and failures without retrying this tick.
         */
        public Drain { results = List.copyOf(results); failures = List.copyOf(failures); }
    }
    /** Earlier children remain committed; callers can reconcile without retrying the consumed tick. */
    public static final class DrainFailure extends IllegalArgumentException {
        private final Drain drain;
        private DrainFailure(Drain drain) { super("Proc drain failed: " + drain.failures()); this.drain = drain; }
        /**
         * Returns the retained immutable partial drain for legacy callers catching this exception.
         */
        public Drain drain() { return drain; }
    }
    /**
     * Internal pairing that preserves the captured session even if the owner later reconnects.
     * @param command admitted immutable child
     * @param session exact launch lifecycle token
     */
    private record Pending(ProcCommand command, Session session) {}

    /**
     * Creating thread shared with the encounter; every externally driven operation checks ownership.
     */
    private final Thread ownerThread = Thread.currentThread();
    private final CombatEncounter encounter;
    private final Limits limits;
    private final RandomSource random;
    /**
     * Current exact lifecycle token per owner; replacing a token invalidates that owner’s queued children and buff.
     */
    private final Map<UUID, Session> sessions = new HashMap<>();
    /**
     * Retained shared buffs by owner; time advancement removes expired values.
     */
    private final Map<UUID, TempoState> tempo = new HashMap<>();
    /**
     * Pending children ordered by due tick then proc UUID, independent of owner-map iteration.
     */
    private final PriorityQueue<Pending> queue = new PriorityQueue<>(Comparator
            .comparingLong((Pending p) -> p.command().dueTick()).thenComparing(p -> p.command().procId()));
    /**
     * Latest accepted coordinator clock; physical admission and draining may advance it.
     */
    private long now;
    /**
     * Last consumed drain tick; repeated same-tick calls cannot replay effects.
     */
    private long lastDrain = -1;
    /**
     * Cumulative child counts for capacity refusal, stale sessions and explicit cleanup respectively.
     */
    private long capacityRejected, inactiveRejected, cleared;
    /**
     * Terminal local state; close clears effects and ends the associated encounter.
     */
    private boolean closed;

    /**
     * Retains nonnull encounter, limits and random source; immediately verifies encounter access
     * on this constructing thread. Owns no external scheduler. The caller must drive and close it.
     */
    public ProcCoordinator(CombatEncounter encounter, Limits limits, RandomSource random) {
        this.encounter = Objects.requireNonNull(encounter);
        this.limits = Objects.requireNonNull(limits);
        this.random = Objects.requireNonNull(random);
        encounter.target(); // Verify that both authorities have the same owner thread.
    }

    /**
     * Bounded admission; reconnect uses a new token. Repeating the current token preserves tempo.
     * <p>
     * Returns false only for a new owner at capacity; returns true for the same active token
     * without resetting state. Replacing an owner's token first clears its old children and Tempo.
     * Closed or wrong-thread access throws IllegalStateException.
     */
    public boolean activate(Session session) {
        requireOpen();
        Objects.requireNonNull(session);
        Session previous = sessions.get(session.ownerId());
        if (session.equals(previous)) return true;
        if (previous == null && sessions.size() >= limits.maxSessions()) return false;
        if (previous != null) clearSession(previous);
        sessions.put(session.ownerId(), session);
        return true;
    }

    /**
     * A late quit callback for an old token cannot erase a reconnected session.
     * <p>
     * Removes children for exactly this token and clears Tempo only if that token is still active.
     * Safe after close on the owning thread; it cannot clear another session for the same UUID.
     */
    public void clearSession(Session session) {
        checkThread();
        if (sessions.remove(session.ownerId(), session)) tempo.remove(session.ownerId());
        int before = queue.size();
        queue.removeIf(p -> p.session().equals(session));
        cleared += before - queue.size();
    }

    /**
     * Advances the nondecreasing game clock, expires old state and returns this exact active
     * session's bonus, otherwise zero. This inspection has clock/expiry side effects and requires open state.
     */
    public int tempoBonus(Session session, long tick) {
        requireOpen(); advance(tick);
        return live(session) ? tempo.getOrDefault(session.ownerId(), new TempoState(0, 0)).bonusAt(tick) : 0;
    }

    /**
     * This is the sole proc-admitting physical entry; accepted DTOs cannot be resubmitted to mint
     * more children. The combat authority rejects duplicate physical keys before queue admission.
     * Offline old arrows may credit captured physical damage, but cannot create buffs or children.
     * <p>
     * Preflights timing and fractional randomness, commits the parent, then builds eligible Tempo
     * and reserves all children or none. Failures before commit can still advance the coordinator
     * clock/expire buffs or consume a draw. Capacity rejection does not undo parent HP/credit or
     * eligible Tempo refresh. Whole hundreds consume no draw. Children retain pre-hit HP policy.
     * @param shot immutable launch snapshot excluding live Tempo
     * @param impact settled candidate with nondecreasing receiver tick
     * @param modifiers physical modifiers applied once by the encounter
     * @param capturedSession exact launch token with matching shot owner
     * @param adapterRejection nonnull optional settled veto
     * @return physical result plus immutable admission details
     */
    public PhysicalResult physical(ShotContext shot, PhysicalImpact impact, DamageModifiers modifiers,
                                   Session capturedSession, Optional<DamageResult.RejectionReason> adapterRejection) {
        requireOpen();
        Objects.requireNonNull(capturedSession);
        if (!shot.ownerId().equals(capturedSession.ownerId())) throw new IllegalArgumentException("Shot/session owner mismatch");
        advance(impact.tick());
        // Validate all timing before physical damage can commit, including the latest possible child.
        Math.addExact(impact.tick(), Math.max(60L, 5L * limits.spacingTicks()));
        int level = EnchantEffects.level(shot.enchantments(), "fatal_tempo", 5);
        TempoState active = live(capturedSession) ? tempo.getOrDefault(capturedSession.ownerId(), new TempoState(0, 0)) : new TempoState(0, 0);
        double effective = Ferocity.effective(shot.stats().effective(StatKey.FEROCITY), active.bonusPercent());
        // Preflight injected randomness before the physical authority can commit damage.
        // A rejected candidate may consume a fractional draw but never admits children.
        int count = Ferocity.count(effective, random);
        var damage = encounter.physical(shot, impact, modifiers, effective, adapterRejection, active);
        if (!damage.accepted()) return new PhysicalResult(damage, Admission.PHYSICAL_REJECTED, 0, List.of());
        buildTempo(capturedSession, level);
        if (count == 0) return new PhysicalResult(damage, Admission.NO_CHILDREN, 0, List.of());
        if (!live(capturedSession)) {
            inactiveRejected += count;
            return new PhysicalResult(damage, Admission.INACTIVE_SESSION, count, List.of());
        }
        // Reserve the entire group; rejection does not silently truncate or retry a physical hit.
        if (count > limits.queueCapacity() - queue.size()) {
            capacityRejected += count;
            return new PhysicalResult(damage, Admission.CAPACITY_REJECTED, count, List.of());
        }
        var commands = new ArrayList<ProcCommand>(count);
        for (int index = 1; index <= count; index++) {
            var command = new ProcCommand(childId(damage.impactId(), index), damage.impactId(), damage.origin(),
                    damage.ownerId(), damage.shotId(), now + (long) index * limits.spacingTicks(),
                    damage.amounts().mitigatedDamage(), damage.crit(), damage.mechanic(), level,
                    Optional.of(encounter.procHealthSnapshot(damage.impactId())));
            commands.add(command);
            queue.add(new Pending(command, capturedSession));
        }
        return new PhysicalResult(damage, Admission.QUEUED, count, commands);
    }

    /**
     * Bounded work even after a late tick; repeated calls at the same tick cannot bypass the budget.
     * <p>
     * Returns immutable results when all children evaluate normally; throws {@link DrainFailure}
     * with the complete partial outcome if any child fails. Same-tick repeats return empty.
     * Due ordering is dueTick then stable child UUID, not insertion order.
     */
    public List<DamageResult> tick(long tick) {
        Drain drain = tickOutcomes(tick);
        if (!drain.failures().isEmpty()) throw new DrainFailure(drain);
        return drain.results();
    }

    /**
     * Per-child transaction boundary; a failed child is consumed and explicitly reported.
     * <p>
     * Advances/expires buffs, drains dueTick/UUID order up to the configured budget, and isolates
     * IllegalArgumentException/ArithmeticException per child. Earlier commits survive failures;
     * each removed child is consumed. Accepted eligible children may refresh Tempo, never recurse.
     * @param tick nonnegative nondecreasing game tick; same-tick repeats return an empty drain
     * @return immutable results and failures; no scheduler or retry is installed
     */
    public Drain tickOutcomes(long tick) {
        requireOpen(); advance(tick);
        if (lastDrain == tick) return new Drain(List.of(), List.of());
        lastDrain = tick;
        var results = new ArrayList<DamageResult>();
        var failures = new ArrayList<ChildFailure>();
        for (int processed = 0; processed < limits.maxDuePerTick() && !queue.isEmpty()
                && queue.peek().command().dueTick() <= tick; processed++) {
            Pending pending = queue.remove();
            if (!live(pending.session())) { inactiveRejected++; continue; }
            try {
                var result = encounter.proc(pending.command(), tick);
                results.add(result);
                if (result.accepted()) buildTempo(pending.session(), pending.command().fatalTempoSourceLevel());
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                failures.add(new ChildFailure(pending.command(), invalid.getMessage()));
            }
            // No child count/admission call here: eligible children build tempo, never descendants.
        }
        return new Drain(results, failures);
    }

    /**
     * Returns current counters on the creating thread, including after close; does not expire buffs.
     */
    public Metrics metrics() {
        checkThread();
        return new Metrics(queue.size(), sessions.size(), tempo.size(), capacityRejected, inactiveRejected, cleared);
    }
    /**
     * Adapter provenance need only remain while one of these bounded parents has queued children.
     * <p>
     * Returns an immutable set of physical parent IDs still referenced by queued children.
     * Adapters may prune their bounded diagnostics for other parents; domain claims remain retained.
     */
    public Set<UUID> pendingParents() {
        checkThread(); var parents = new HashSet<UUID>();
        queue.forEach(p -> parents.add(p.command().parentImpactId())); return Set.copyOf(parents);
    }

    /**
     * Returns a stable UTF-8 name UUID from parent ID and sibling index 1–5; null parent and
     * out-of-range index reject. This pure helper is callable without owning-thread access.
     */
    public static UUID childId(UUID parent, int index) {
        Objects.requireNonNull(parent);
        if (index < 1 || index > 5) throw new IllegalArgumentException("Child index must be 1–5");
        return UUID.nameUUIDFromBytes(("ferocity:" + parent + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    /** Terminal reset/shutdown; stale callbacks fail closed and a new encounter needs a new instance. */
    @Override public void close() {
        checkThread();
        if (closed) return;
        closed = true;
        cleared += queue.size();
        queue.clear(); tempo.clear(); sessions.clear();
        encounter.end();
    }

    /**
     * Compares the complete captured Session against the current owner entry, not UUID alone.
     */
    private boolean live(Session session) { return session.equals(sessions.get(session.ownerId())); }
    /**
     * Only an eligible positive captured level in the exact live session refreshes the shared
     * state at coordinator time. Called after successful parent/child damage, never for rejection.
     */
    private void buildTempo(Session session, int level) {
        if (level > 0 && live(session)) {
            tempo.put(session.ownerId(), tempo.getOrDefault(session.ownerId(), new TempoState(0, 0)).hit(level, now));
        }
    }
    /**
     * Rejects negative/backward/unrepresentable expiry time before storing the clock, then removes
     * Tempo whose exclusive expiry is reached. Tick draining and physical admission share this clock.
     */
    private void advance(long tick) {
        DomainChecks.nonNegative(tick, "tick");
        if (tick < now) throw new IllegalArgumentException("Coordinator clock cannot move backwards");
        Math.addExact(tick, 60); // Expiry refresh must be representable before any mutation.
        now = tick;
        tempo.values().removeIf(state -> state.expiresAt() <= tick);
    }
    /**
     * Enforces thread confinement and terminal closure before mutable admission/drain operations.
     */
    private void requireOpen() { checkThread(); if (closed) throw new IllegalStateException("Proc coordinator is closed"); }
    /**
     * Throws IllegalStateException outside the creating thread; immutable returned DTOs may be retained.
     */
    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Proc coordinator accessed outside its owner thread");
    }
}
