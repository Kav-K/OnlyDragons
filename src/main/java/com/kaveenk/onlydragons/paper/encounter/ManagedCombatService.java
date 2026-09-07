package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.application.fire.OwnedFireCoordinator;
import com.kaveenk.onlydragons.application.proc.ProcCoordinator;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.combat.DamageResult;
import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.encounter.CombatEncounter;
import com.kaveenk.onlydragons.domain.encounter.EncounterResult;
import com.kaveenk.onlydragons.domain.encounter.TargetState;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import com.kaveenk.onlydragons.domain.projectile.SettledHit;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Plugin-lifetime settled-hit receiver and owner of each generation's combat/proc/fire graph.
 * {@link OwnedBowService} supplies the single physical claim and native suppression;
 * this adapter commits it through domain accounting once, using receiver commit time
 * while preserving collision time. Native health is a projection, never another input
 * to damage. All operations are server-thread-confined; read-only notifications reject
 * mutation/reentry. Defeat closes damage admission before native death can reenter,
 * while the backend may retain native ownership through animation.
 * @see TargetBackend
 * @see ProcCoordinator
 * @see OwnedFireCoordinator
 */
public final class ManagedCombatService implements AutoCloseable {
    /**
     * Removal handle for a post-processing observer; close once outside read-only notification.
     * Removal uses consumer equality rather than a registration token. Repeated closes
     * can remove another equal registration, so this handle is not generally idempotent.
     */
    public interface Observation extends AutoCloseable { /** Removes the first equal consumer on the server thread, outside read-only notification; call once.
 * @throws IllegalStateException for forbidden thread or notification-time mutation
 */ @Override void close(); }
    /**
     * Domain adapter lifecycle: ACTIVE admits damage, DEFEATED retains a frozen result,
     * and TERMINATED ended without normal defeat. DEFEATED may still own a native animation.
     */
    public enum State { ACTIVE, DEFEATED, TERMINATED }
    /**
     * Immutable captured-hit explanation, distinct from current equipment or native HP.
     * @param damage authoritative accepted/rejected accounting result
     * @param shot captured physical source reused by descendants
     * @param collisionTick original native collision tick, not receiver commit time
     * @param admission proc scheduling disposition for this result
     * @param healthRemaining domain HP after this accounting step
     * @param total cumulative actual-HP and contribution totals for this owner
     */
    public record Explanation(DamageResult damage, ShotContext shot, long collisionTick,
                            ProcCoordinator.Admission admission, double healthRemaining,
                            EncounterResult.Contribution total) {
        /**
         * Formats separate HP, credit, totals and rejection status without changing precision in storage.
         * @return non-null development diagnostic text
         */
        public String summary() {
            var a = damage.amounts();
            return "Combat " + damage.kind() + " " + damage.crit() + " | HP removed=" + a.actualHealthDamage()
                    + " credit=" + a.contributionDamage() + " | total HP=" + total.actualHealthDamage()
                    + " credit=" + total.contributionDamage() + " | remaining=" + healthRemaining
                    + " | " + damage.rejectionReason().map(Enum::name).orElse("accepted");
        }
    }

    /**
     * Immutable service-produced projection; recent diagnostics do not replace the full ledger.
     * This record itself does not copy arbitrary collections supplied by external callers.
     * @param encounterId unique generation UUID
     * @param ownerId control owner, independent of shooter participants
     * @param entityId native parent UUID retained through animation
     * @param state adapter lifecycle, distinct from native liveness
     * @param target current immutable authoritative domain target
     * @param contributions immutable per-participant totals
     * @param impacts at most 64 latest accepted domain results in service-produced views
     * @param completion frozen defeat result, empty for active/nondefeated termination
     * @param procs queue/session metrics
     * @param acceptedImpacts total accepted ordinal count, including accepted zero damage
     * @param omittedImpacts accepted results outside the64-entry diagnostic window
     * @param retainedParents physical source snapshots still required by queued procs
     */
    public record View(UUID encounterId, UUID ownerId, UUID entityId, State state, TargetState target,
                    Map<UUID, EncounterResult.Contribution> contributions, List<DamageResult> impacts,
                    Optional<EncounterResult> completion, ProcCoordinator.Metrics procs, long acceptedImpacts, long omittedImpacts, int retainedParents) {}
    /**
     * One admitted generation's native backend and authoritative combat/proc/fire owners.
     * Exact activated session values prevent listener ordering from losing old-token cleanup.
     * Parent diagnostic maps retain only sources needed by pending Ferocity children.
     */
    private static final class Fight {
        final UUID id, owner, entityId;
        final TargetBackend backend;
        final BoundingBox bounds;
        final CombatEncounter combat;
        final ProcCoordinator procs;
        final OwnedFireCoordinator fire;
        final Map<UUID, ProcCoordinator.Session> sessions = new HashMap<>();
        final Map<UUID, ShotContext> shots = new HashMap<>();
        final Map<UUID, Long> collisions = new HashMap<>();
        State state = State.ACTIVE;
        /**
         * Captures native identity and detached bounds and allocates the bounded fire owner.
         * @param id encounter generation
         * @param owner control/reset owner
         * @param backend already constructed native projection
         * @param bounds arena box copied before retention
         * @param combat authoritative domain encounter
         * @param procs its bounded proc coordinator
         */
        Fight(UUID id, UUID owner, TargetBackend backend, BoundingBox bounds, CombatEncounter combat, ProcCoordinator procs) {
            this.id = id; this.owner = owner; this.entityId = backend.entity().getUniqueId(); this.backend = backend; this.bounds = bounds.clone(); this.combat = combat; this.procs = procs;
            this.fire = new OwnedFireCoordinator(combat, 128);
        }
        /**
         * Checks actual world plus feet position against this fight's native box.
         * @param player live participant candidate
         * @return true when inside this target's world and arena
         */
        boolean contains(Player player) { return player.getWorld().equals(backend.entity().getWorld()) && bounds.contains(player.getLocation().toVector()); }
        /**
         * Builds detached domain/metric diagnostics with a64-result history window.
         * @return current immutable service projection without mutating accounting
         */
        View view() { return new View(id, owner, entityId, state, combat.target(), combat.contributions(),
                combat.recentImpacts(64), combat.completion(), procs.metrics(), combat.acceptedOrdinal(), Math.max(0, combat.acceptedOrdinal() - 64), shots.size()); }
    }

    private final JavaPlugin plugin;
    private final OwnedBowService bows;
    private final Map<UUID, Fight> fights = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, View> history = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Explanation> last = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, EncounterResult> completions = new LinkedHashMap<>();
    private final List<Consumer<SettledHit>> observers = new ArrayList<>();
    private final Predicate<UUID> protection = this::ownsEntity;
    private final Consumer<SettledHit> receiver = this::receive;
    private final EnchantEffects effects = EnchantEffects.checkpointTwo();
    private BukkitTask task;
    private boolean closed, notifying;
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();
    /**
     * Binds the composition root and its existing physical owner without starting tasks.
     * @param plugin scheduler/logging owner
     * @param bows sole settled-hit producer and native retained-protection ingress
     */
    public ManagedCombatService(JavaPlugin plugin, OwnedBowService bows) { this.plugin = plugin; this.bows = bows; }
    /**
     * Attaches the one accounting receiver/retained protection and starts one per-tick drain.
     * @throws IllegalStateException if already started, closed, off-thread or notifying
     */
    public void start() {
        check();
        if (task != null) throw new IllegalStateException("Already started");
        bows.receiver(receiver);
        bows.retainedProtection(protection);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    /**
     * Registers one of at most eight read-only observers after production processing.
     * An observer cannot replace accounting or mutate shared controls. Failures are
     * isolated and diagnosed; the returned handle removes the first equal consumer.
     * @param observer non-null recipient of the immutable physical claim/rejection
     * @return call-once removal handle; repeated closes may remove another equal registration
     * @throws IllegalStateException for capacity, thread, closed state or observer reentry
     * @throws NullPointerException if observer is null
     */
    public Observation observeSettled(Consumer<SettledHit> observer) {
        check();
        Objects.requireNonNull(observer);
        if (observers.size() >= 8) throw new IllegalStateException("Observer capacity reached");
        observers.add(observer);
        return () -> {
            mutation();
            observers.remove(observer);
        };
    }

    /**
     * Checks thread, observer guard and open lifecycle before external controls allocate
     * native state or persist configuration.
     * @throws IllegalStateException if mutation is not currently allowed
     */
    public void requireMutable() {
        check();
    }

    /**
     * Checks thread and observer reentry while permitting idempotent cleanup after close.
     * @throws IllegalStateException if off-thread or inside a read-only notification
     */
    public void requireMutationAllowed() {
        mutation();
    }

    /**
     * Runs a synchronous notification with shared-control mutation disabled in a finally guard.
     * Does not swallow recipient exceptions; the notification owner must isolate recipients.
     * @param notification read-only notification batch
     * @throws IllegalStateException for wrong thread or nested read-only notification
     */
    public void readOnlyNotification(Runnable notification) {
        mutation();
        notifying = true;
        try {
            notification.run();
        } finally {
            notifying = false;
        }
    }

    /**
     * Admits a backend into the single bow/target/combat pipeline under a fresh generation.
     * Allows at most16 retained fights and one per control owner. Capacity/overlap and
     * domain construction precede native admission; callers own backend cleanup if those
     * early checks fail. Failures inside admission attempt to close proc, bow and backend
     * resources. Selection and profile stay immutable for this generation.
     * @param owner non-null control/reset UUID, not necessarily an online player
     * @param backend non-null already created native target projection
     * @param bounds copied world-space admission box in blocks
     * @param hp finite positive domain maximum/current HP, validated by TargetState
     * @param defense finite nonnegative domain defense points
     * @param variant trusted target/type identifier
     * @param profile immutable mitigation, cap and proc-health policy
     * @param selection optional full catalog provenance; empty for explicit practice calibration
     * @param random bounded probability source used by this generation's proc decisions
     * @return fresh encounter UUID after admission and initial session reconciliation
     * @throws IllegalArgumentException for capacity/owner/target/domain/admission failures
     * @throws IllegalStateException for thread, closed/reentrant mutation or native setup failures
     */
    public UUID open(UUID owner, TargetBackend backend, BoundingBox bounds, double hp, double defense,
            String variant, CombatProfile profile, Optional<DragonCatalog.Selection> selection,
            RandomSource random) {
        check();
        Objects.requireNonNull(owner);
        Objects.requireNonNull(backend);
        if (fights.size() >= 16 || fights.values().stream().anyMatch(fight -> fight.owner.equals(owner)))
            throw new IllegalArgumentException("Reset your existing practice target first or wait for capacity.");
        UUID id = UUID.randomUUID();
        var combat = new CombatEncounter(new TargetState(id, UUID.randomUUID(), hp, hp, defense), variant, profile, selection);
        var procs = new ProcCoordinator(combat, new ProcCoordinator.Limits(4096, 256, 128, 2), random);
        var fight = new Fight(id, owner, backend, bounds, combat, procs);
        boolean admitted = false;
        try {
            bows.openEncounter(id, backend.entity().getWorld(), bounds, profile.mechanic());
            admitted = true;
            bows.registerTarget(id, combat.target().targetId(), backend.entity());
            backend.synchronize(combat.target());
            fights.put(id, fight);
            reconcile(fight);
            return id;
        } catch (RuntimeException failure) {
            fights.remove(id);
            procs.close();
            if (admitted) bows.endEncounter(id);
            backend.close();
            throw failure;
        }
    }

    /**
     * Reads retained fight ownership, including a defeated native animation.
     * @param owner control owner UUID
     * @return current retained fight, or empty after release; history is not searched
     */
    public Optional<View> owned(UUID owner) {
        thread(); return fights.values().stream().filter(fight -> fight.owner.equals(owner)).findFirst().map(Fight::view);
    }

    /**
     * Reads a live view or bounded retained history after native release.
     * @param id encounter generation UUID
     * @return empty for unknown or evicted generations
     */
    public Optional<View> view(UUID id) {
        thread(); Fight fight = fights.get(id); return Optional.ofNullable(fight == null ? history.get(id) : fight.view());
    }

    /**
     * Reads the latest bounded diagnostic for a shooter, not a live damage calculation.
     * @param owner captured participant UUID
     * @return last explanation, empty before a hit or after session cleanup/eviction
     */
    public Optional<Explanation> last(UUID owner) { thread(); return Optional.ofNullable(last.get(owner)); }
    /**
     * Reads bounded frozen completions without granting rewards or replaying announcements.
     * @return immutable completion list in retained insertion order, at most64
     */
    public List<EncounterResult> completions() { thread(); return List.copyOf(completions.values()); }
    /**
     * Counts retained fight objects, including domain-defeated native animation.
     * @return current resource-owning fight count, not only State.ACTIVE entries
     */
    public int activeCount() { thread(); return fights.size(); }
    /**
     * Reads one retained generation's burn/vulnerability metrics.
     * @param encounter generation UUID
     * @return immutable metrics, all zero when no retained fight exists
     */
    public OwnedFireCoordinator.Metrics fireMetrics(UUID encounter) {
        thread(); var fight = fights.get(encounter);
        return fight == null ? new OwnedFireCoordinator.Metrics(0, 0, 0) : fight.fire.metrics();
    }

    /**
     * Reports this service's one synchronous scheduler handle.
     * @return one while started, otherwise zero
     */
    public int taskCount() { thread(); return task == null ? 0 : 1; }
    /**
     * Tests retained native parent ownership independently from entity liveness.
     * The bow guard uses this to suppress unmanaged damage during native death animation.
     * @param entity native parent UUID
     * @return true while a fight retains the backend, including DEFEATED
     */
    public boolean ownsEntity(UUID entity) { thread(); return fights.values().stream().anyMatch(fight -> fight.entityId.equals(entity)); }
    /**
     * Terminates fights controlled by this owner without creating a defeat result.
     * @param owner control UUID; absent owners are a no-op
     * @throws IllegalStateException for closed service, wrong thread or observer reentry
     */
    public void reset(UUID owner) {
        check();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.owner.equals(owner)) {
                terminate(fight);
            }
        }
    }

    /**
     * Clears exact activated proc/fire sessions even if another listener already cleared bows.
     * Already airborne shots retain captured ownership, but cannot recreate an inactive
     * session's buffs/children. Reconciliation preserves another owner's live state.
     * @param owner dead/disconnected player UUID
     * @throws IllegalStateException for wrong thread or observer reentry
     */
    public void playerEnded(UUID owner) {
        mutation();
        last.remove(owner);
        for (Fight fight : List.copyOf(fights.values())) {
            ProcCoordinator.Session old = fight.sessions.remove(owner);
            if (old != null) {
                fight.procs.clearSession(old);
                fight.fire.clearSession(old);
            }
            reconcile(fight);
        }
    }

    /**
     * Forwards final native death cancellation to the matching retained backend.
     * @param entity native parent UUID
     * @param cancelled final native death cancellation; distinct from owned damage suppression
     * @throws IllegalStateException for wrong thread or observer reentry
     */
    public void nativeDeathObserved(UUID entity, boolean cancelled) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity)) {
                fight.backend.deathObserved(cancelled);
            }
        }
    }

    /**
     * Forwards actual public removal before later backend polling.
     * @param entity native parent UUID
     * @param cause immediate removal cause
     * @throws IllegalStateException for wrong thread or observer reentry
     */
    public void nativeRemoved(UUID entity, EntityRemoveEvent.Cause cause) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity)) {
                fight.backend.removed(cause);
            }
        }
    }

    /**
     * Terminates unexpected native death only while domain combat remains ACTIVE.
     * The normal lethal path has already marked DEFEATED before native event reentry.
     * @param entity native parent UUID
     * @throws IllegalStateException for wrong thread or observer reentry
     */
    public void entityEnded(UUID entity) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity) && fight.state == State.ACTIVE) {
                terminate(fight);
            }
        }
    }

    /**
     * Clears stale/absent/dead/outside sessions before admitting current live bow tokens.
     * Admission capacity failure cannot invent a session. Every drain and physical receive
     * uses this reconciliation so late captured arrows cannot resurrect buffs.
     * @param fight active generation being reconciled
     */
    private void reconcile(Fight fight) {
        if (fight.state != State.ACTIVE) return;
        for (var old : List.copyOf(fight.sessions.values())) {
            Player player = Bukkit.getPlayer(old.ownerId());
            if (!bows.isCurrentSession(old.ownerId(), old.token()) || player == null || !player.isOnline() || player.isDead() || !fight.contains(player)) {
                fight.procs.clearSession(old);
                fight.fire.clearSession(old);
                fight.sessions.remove(old.ownerId(), old);
            }
        }
        prune(fight);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead() && fight.contains(player)) {
                bows.currentSession(player.getUniqueId()).ifPresent(token -> {
                    var session = new ProcCoordinator.Session(player.getUniqueId(), token);
                    if (!session.equals(fight.sessions.get(player.getUniqueId())) && fight.procs.activate(session)) {
                        fight.sessions.put(player.getUniqueId(), session);
                        fight.fire.activate(session);
                    }
                });
            }
        }
    }

    /**
     * Drops physical source diagnostics once no queued Ferocity child needs them.
     * Fire owns its own immutable source, so this pruning cannot change burn attribution.
     * @param fight generation whose pending-parent set is authoritative
     */
    private void prune(Fight fight) {
        var needed = fight.procs.pendingParents();
        fight.shots.keySet().retainAll(needed);
        fight.collisions.keySet().retainAll(needed);
    }

    /**
     * Returns bounded failures without exposing mutable internal buffers.
     * @return immutable last32 warning strings
     */
    public List<String> diagnostics() { thread(); return List.copyOf(diagnostics); }
    /**
     * Retains the newest32 failures and surfaces each through plugin logging.
     * @param message failure diagnostic, not player/accounting input
     */
    private void diagnostic(String message) {
        diagnostics.addLast(message); while (diagnostics.size() > 32) diagnostics.removeFirst();
        plugin.getLogger().warning(message);
    }

    /**
     * Consumes one settled physical claim at the receiver's actual current tick.
     * Reconciles sessions, computes physical modifiers once, admits procs/fire, explains
     * and mirrors the committed result. A failure preserves commits and retires the fight.
     * Observers run afterward under a read-only guard, even for claims without an active fight.
     * @param hit immutable physical source and adapter rejection, never a new native event
     */
    private void receive(SettledHit hit) {
        if (closed) return;
        Fight fight = fights.get(hit.impact().key().encounterId());
        if (fight != null && fight.state == State.ACTIVE) {
            try {
                reconcile(fight);
                ShotContext shot = hit.projectile().shot();
                // The receiver's current commit tick is authoritative; collision remains separately retained.
                long now = Integer.toUnsignedLong(Bukkit.getCurrentTick());
                var impact = new PhysicalImpact(hit.impact().key(), hit.impact().ownerId(), now, hit.impact().position(), hit.impact().targetPart());
                var result = fight.procs.physical(shot, impact, effects.modifiers(shot, impact.position(), fight.backend.airborne()),
                        new ProcCoordinator.Session(shot.ownerId(), hit.projectile().sessionToken()), rejection(hit));
                if (!result.children().isEmpty()) {
                    fight.shots.put(result.damage().impactId(), shot);
                    fight.collisions.put(result.damage().impactId(), hit.collisionTick());
                }
                var session = new ProcCoordinator.Session(shot.ownerId(), hit.projectile().sessionToken());
                if (session.equals(fight.sessions.get(shot.ownerId())))
                    fight.fire.physical(shot, result.damage(), hit.collisionTick(), session);
                explain(fight, result.damage(), shot, hit.collisionTick(), result.admission());
                prune(fight);
                synchronize(fight);
            } catch (RuntimeException failure) { failed(fight, failure); }
        }
        notifying = true;
        try {
            for (var observer : List.copyOf(observers)) {
                try { observer.accept(hit); } catch (RuntimeException failure) { diagnostic("Settled-hit observer failed: " + failure); }
            }
        } finally { notifying = false; }
    }

    /**
     * Maps physical adapter veto/lifecycle reasons into accounting rejection categories.
     * @param hit settled candidate with optional adapter rejection
     * @return empty for admitted candidates, otherwise a zero-credit domain reason
     */
    private Optional<DamageResult.RejectionReason> rejection(SettledHit hit) {
        return hit.rejection().map(reason -> switch (reason) {
            case PHYSICAL_VETO, NATIVE_VETO -> DamageResult.RejectionReason.CANCELLED;
            case ENCOUNTER_ENDED -> DamageResult.RejectionReason.ENCOUNTER_ENDED;
            case TARGET_DEAD -> DamageResult.RejectionReason.TARGET_DEAD;
            case OUTSIDE_ARENA -> DamageResult.RejectionReason.OUTSIDE_ARENA;
            case TARGET_CHANGED, UNSUPPORTED_PHASE -> DamageResult.RejectionReason.INVALID_TARGET;
        });
    }

    /**
     * Reconciles each active fight, drains bounded Ferocity before fire, then projects HP.
     * Nonactive backends release only after native removal. A failed child is consumed,
     * earlier commits are retained and further admission closes; no damage is applied twice.
     */
    private void tick() {
        if (closed) return;
        long now = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        for (Fight fight : List.copyOf(fights.values())) {
            try {
                if (fight.state != State.ACTIVE) {
                    if (fight.backend.released()) {
                        release(fight);
                    }
                    continue;
                }
                if (!fight.backend.entity().isValid() || fight.backend.entity().isDead()) {
                    terminate(fight);
                    continue;
                }
                reconcile(fight);
                var drain = fight.procs.tickOutcomes(now);
                for (var result : drain.results()) explain(fight, result, fight.shots.get(result.parentImpactId().orElse(result.impactId())),
                        fight.collisions.getOrDefault(result.parentImpactId().orElse(result.impactId()), result.tick()), ProcCoordinator.Admission.NO_CHILDREN);
                prune(fight);
                if (drain.failures().isEmpty() && fight.combat.target().alive()) {
                    for (var fire : fight.fire.tick(now)) explain(fight, fire.damage(), fire.source().shot(),
                            fire.source().collisionTick(), ProcCoordinator.Admission.NO_CHILDREN);
                }
                // Both parent and drain already mutated the domain. Never apply their damage again.
                synchronize(fight);
                if (!drain.failures().isEmpty()) {
                    for (var failure : drain.failures()) tell(failure.command().ownerId(), "Combat proc failed: " + failure.reason()
                            + "; earlier committed HP/credit retained.");
                    if (fight.state == State.ACTIVE) terminate(fight);
                }
            } catch (RuntimeException failure) { failed(fight, failure); }
        }
    }

    /**
     * Retains a bounded captured-source explanation and notifies an in-arena live owner.
     * @param fight authoritative generation
     * @param damage already committed or rejected result
     * @param shot retained physical source; never read from current equipment
     * @param collision original collision tick
     * @param admission proc scheduling outcome for diagnostic presentation
     */
    private void explain(Fight fight, DamageResult damage, ShotContext shot, long collision, ProcCoordinator.Admission admission) {
        var total = fight.combat.contributions().getOrDefault(damage.ownerId(), new EncounterResult.Contribution(0, 0, 0, false));
        var explanation = new Explanation(damage, shot, collision, admission, fight.combat.target().currentHealth(), total);
        putBounded(last, damage.ownerId(), explanation, 128);
        Player player = Bukkit.getPlayer(damage.ownerId());
        if (player != null && fight.contains(player)) {
            player.sendMessage(PresentationFormatter.message(explanation.summary()));
            player.sendActionBar(PresentationFormatter.combat(damage, explanation.healthRemaining()));
        }
    }

    /**
     * Freezes service completion and closes proc/fire admission before native HP-zero reentry.
     * Then ends bow eligibility and lets the backend handle native defeat/removal. Active
     * nonlethal calls only mirror existing domain state and advance native motion.
     * @param fight authoritative generation; no returned damage is reapplied
     */
    private void synchronize(Fight fight) {
        Optional<EncounterResult> result = fight.combat.completion();
        if (result.isPresent() && fight.state == State.ACTIVE) {
            // Publish immutable completion and close admission BEFORE native health zero/death can reenter.
            fight.state = State.DEFEATED;
            fight.fire.close();
            fight.procs.close();
            fight.sessions.clear();
            EncounterResult frozen = result.get();
            if (!completions.containsKey(frozen.completionId())) {
                putBounded(completions, frozen.completionId(), frozen, 64);
                if (fight.backend.announcesImmediately()) frozen.participants().keySet().forEach(owner -> tell(owner, "Practice complete " + frozen.completionId()
                        + " | HP=" + frozen.participants().get(owner).actualHealthDamage()
                        + " credit=" + frozen.participants().get(owner).contributionDamage()));
            }
            fight.backend.synchronize(fight.combat.target());
            bows.endEncounter(fight.id);
            fight.backend.defeated(frozen);
            if (fight.backend.released()) release(fight);
        } else if (fight.state == State.ACTIVE) fight.backend.synchronize(fight.combat.target());
    }

    /**
     * Attempts to preserve a completion after callback/projection failure, then retires.
     * Recovery cannot erase committed HP/credit or authorize a retry of the original hit.
     * @param fight failing generation
     * @param failure original failure surfaced to its control owner
     */
    private void failed(Fight fight, RuntimeException failure) {
        // A callback/projection failure cannot erase a committed result or invite a retry.
        try {
            synchronize(fight);
        } catch (RuntimeException recoveryFailure) {
            diagnostic("Recovery projection failed: " + recoveryFailure);
        } finally {
            tell(fight.owner, "Practice stopped: " + failure.getMessage() + "; committed accounting retained.");
            terminate(fight);
        }
    }

    /**
     * Closes a retained generation's proc/fire/session/arrow/backend resources.
     * Known extension cleanup failures are diagnosed so later cleanup can still run;
     * already defeated state/completion is retained rather than rewritten as a new result.
     * @param fight generation to retire; an already released one is ignored
     */
    private void terminate(Fight fight) {
        if (!fights.containsKey(fight.id)) return;
        if (fight.state == State.ACTIVE) fight.state = State.TERMINATED;
        // Every resource is attempted even when an extension backend fails.
        try {
            fight.procs.close();
        } catch (RuntimeException failure) {
            diagnostic("Proc cleanup failed: " + failure);
        }
        fight.fire.close();
        fight.sessions.clear();
        fight.shots.clear();
        fight.collisions.clear();
        try {
            bows.endEncounter(fight.id);
        } catch (RuntimeException failure) {
            diagnostic("Bow cleanup failed: " + failure);
        }
        try {
            fight.backend.close();
        } catch (RuntimeException failure) {
            diagnostic("Backend cleanup failed: " + failure);
        } finally {
            release(fight);
        }
    }

    /**
     * Keeps a bounded final view and removes the live fight even if history projection fails.
     * @param fight backend whose ownership has ended; no native replacement is created
     */
    private void release(Fight fight) {
        fight.fire.close();
        fight.shots.clear();
        fight.collisions.clear();
        try {
            putBounded(history, fight.id, fight.view(), 32);
        } catch (RuntimeException failure) {
            diagnostic("History projection failed: " + failure);
        } finally {
            fights.remove(fight.id);
        }
    }

    /**
     * Reinserts a key as newest and evicts oldest diagnostic entries beyond capacity.
     * This helper never prunes domain idempotency or contribution history.
     * @param <K> diagnostic key type
     * @param <V> retained immutable value type
     * @param map insertion-ordered diagnostic map
     * @param key updated key
     * @param value replacement value
     * @param capacity maximum retained entries
     */
    private static <K,V> void putBounded(LinkedHashMap<K,V> map, K key, V value, int capacity) {
        map.remove(key); map.put(key, value); while (map.size() > capacity) map.remove(map.keySet().iterator().next());
    }

    /**
     * Sends literal diagnostics only to a currently online UUID, with no offline delivery queue.
     * @param owner recipient UUID
     * @param message literal feedback
     */
    private static void tell(UUID owner, String message) {
        Player player = Bukkit.getPlayer(owner); if (player != null && player.isOnline()) player.sendMessage(Component.text(message));
    }

    /**
     * Permanently stops the loop, attempts every fight cleanup and detaches only its own
     * receiver/protection registrations. Bounded history/observers are cleared in finally.
     * @throws IllegalStateException for off-thread access or read-only notification reentry
     */
    public void close() {
        mutation();
        if (closed) return;
        closed = true;
        if (task != null) task.cancel();
        task = null;
        try {
            for (Fight fight : List.copyOf(fights.values())) {
                try {
                    terminate(fight);
                } catch (RuntimeException failure) {
                    diagnostic("Fight cleanup failed: " + failure);
                }
            }
        } finally {
            bows.clearReceiver(receiver);
            bows.clearRetainedProtection(protection);
            observers.clear();
            history.clear();
            last.clear();
            completions.clear();
            fights.clear();
        }
    }

    /**
     * Enforces classic Paper server-thread access to native entities and mutable service state.
     */
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Managed combat requires server thread");
    }

    /**
     * Rejects observer reentry while permitting cleanup on an already closed service.
     */
    private void mutation() {
        thread();
        if (notifying) throw new IllegalStateException("Settled-hit observers are read-only");
    }

    /**
     * Requires an open mutable service before admission or reset operations.
     */
    private void check() {
        mutation();
        if (closed) throw new IllegalStateException("Managed combat closed");
    }

}
