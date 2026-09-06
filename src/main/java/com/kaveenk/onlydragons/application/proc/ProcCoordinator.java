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
    public record Session(UUID ownerId, UUID token) {
        public Session { Objects.requireNonNull(ownerId); Objects.requireNonNull(token); }
    }
    public record Limits(int queueCapacity, int maxDuePerTick, int maxSessions, int spacingTicks) {
        public Limits {
            if (queueCapacity < 1 || maxDuePerTick < 1 || maxSessions < 1 || spacingTicks < 1) {
                throw new IllegalArgumentException("Queue/session limits and spacing must be positive");
            }
        }
    }
    public enum Admission { QUEUED, NO_CHILDREN, CAPACITY_REJECTED, INACTIVE_SESSION, PHYSICAL_REJECTED }
    public record PhysicalResult(DamageResult damage, Admission admission, int requestedChildren,
                                 List<ProcCommand> children) {
        public PhysicalResult { children = List.copyOf(children); }
    }
    public record Metrics(int queued, int sessions, int tempoStates, long capacityRejectedChildren,
                          long inactiveRejectedChildren, long clearedChildren) {}
    public record ChildFailure(ProcCommand command, String reason) {}
    public record Drain(List<DamageResult> results, List<ChildFailure> failures) {
        public Drain { results = List.copyOf(results); failures = List.copyOf(failures); }
    }
    /** Earlier children remain committed; callers can reconcile without retrying the consumed tick. */
    public static final class DrainFailure extends IllegalArgumentException {
        private final Drain drain;
        private DrainFailure(Drain drain) { super("Proc drain failed: " + drain.failures()); this.drain = drain; }
        public Drain drain() { return drain; }
    }
    private record Pending(ProcCommand command, Session session) {}

    private final Thread ownerThread = Thread.currentThread();
    private final CombatEncounter encounter;
    private final Limits limits;
    private final RandomSource random;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, TempoState> tempo = new HashMap<>();
    private final PriorityQueue<Pending> queue = new PriorityQueue<>(Comparator
            .comparingLong((Pending p) -> p.command().dueTick()).thenComparing(p -> p.command().procId()));
    private long now;
    private long lastDrain = -1;
    private long capacityRejected, inactiveRejected, cleared;
    private boolean closed;

    public ProcCoordinator(CombatEncounter encounter, Limits limits, RandomSource random) {
        this.encounter = Objects.requireNonNull(encounter);
        this.limits = Objects.requireNonNull(limits);
        this.random = Objects.requireNonNull(random);
        encounter.target(); // Verify that both authorities have the same owner thread.
    }

    /** Bounded admission; reconnect uses a new token. Repeating the current token preserves tempo. */
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

    /** A late quit callback for an old token cannot erase a reconnected session. */
    public void clearSession(Session session) {
        checkThread();
        if (sessions.remove(session.ownerId(), session)) tempo.remove(session.ownerId());
        int before = queue.size();
        queue.removeIf(p -> p.session().equals(session));
        cleared += before - queue.size();
    }

    public int tempoBonus(Session session, long tick) {
        requireOpen(); advance(tick);
        return live(session) ? tempo.getOrDefault(session.ownerId(), new TempoState(0, 0)).bonusAt(tick) : 0;
    }

    /**
     * This is the sole proc-admitting physical entry; accepted DTOs cannot be resubmitted to mint
     * more children. The combat authority rejects duplicate physical keys before queue admission.
     * Offline old arrows may credit captured physical damage, but cannot create buffs or children.
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
        double effective = Ferocity.effective(shot.stats().effective(StatKey.FEROCITY), tempoBonus(capturedSession, now));
        // Preflight injected randomness before the physical authority can commit damage.
        // A rejected candidate may consume a fractional draw but never admits children.
        int count = Ferocity.count(effective, random);
        var damage = encounter.physical(shot, impact, modifiers, effective, adapterRejection);
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
                    damage.amounts().mitigatedDamage(), damage.crit(), damage.mechanic(), level);
            commands.add(command);
            queue.add(new Pending(command, capturedSession));
        }
        return new PhysicalResult(damage, Admission.QUEUED, count, commands);
    }

    /** Bounded work even after a late tick; repeated calls at the same tick cannot bypass the budget. */
    public List<DamageResult> tick(long tick) {
        Drain drain = tickOutcomes(tick);
        if (!drain.failures().isEmpty()) throw new DrainFailure(drain);
        return drain.results();
    }

    /** Per-child transaction boundary; a failed child is consumed and explicitly reported. */
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

    public Metrics metrics() {
        checkThread();
        return new Metrics(queue.size(), sessions.size(), tempo.size(), capacityRejected, inactiveRejected, cleared);
    }

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

    private boolean live(Session session) { return session.equals(sessions.get(session.ownerId())); }
    private void buildTempo(Session session, int level) {
        if (level > 0 && live(session)) {
            tempo.put(session.ownerId(), tempo.getOrDefault(session.ownerId(), new TempoState(0, 0)).hit(level, now));
        }
    }
    private void advance(long tick) {
        DomainChecks.nonNegative(tick, "tick");
        if (tick < now) throw new IllegalArgumentException("Coordinator clock cannot move backwards");
        Math.addExact(tick, 60); // Expiry refresh must be representable before any mutation.
        now = tick;
        tempo.values().removeIf(state -> state.expiresAt() <= tick);
    }
    private void requireOpen() { checkThread(); if (closed) throw new IllegalStateException("Proc coordinator is closed"); }
    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Proc coordinator accessed outside its owner thread");
    }
}
