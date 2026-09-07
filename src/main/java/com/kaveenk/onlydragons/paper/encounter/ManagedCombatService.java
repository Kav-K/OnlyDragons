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

/** One plugin-lifetime physical receiver; every generation owns one combat/proc authority. */
public final class ManagedCombatService implements AutoCloseable {
    public interface Observation extends AutoCloseable { @Override void close(); }
    public enum State { ACTIVE, DEFEATED, TERMINATED }
    public record Explanation(DamageResult damage, ShotContext shot, long collisionTick,
                            ProcCoordinator.Admission admission, double healthRemaining,
                            EncounterResult.Contribution total) {
        public String summary() {
            var a = damage.amounts();
            return "Combat " + damage.kind() + " " + damage.crit() + " | HP removed=" + a.actualHealthDamage()
                    + " credit=" + a.contributionDamage() + " | total HP=" + total.actualHealthDamage()
                    + " credit=" + total.contributionDamage() + " | remaining=" + healthRemaining
                    + " | " + damage.rejectionReason().map(Enum::name).orElse("accepted");
        }
    }

    public record View(UUID encounterId, UUID ownerId, UUID entityId, State state, TargetState target,
                    Map<UUID, EncounterResult.Contribution> contributions, List<DamageResult> impacts,
                    Optional<EncounterResult> completion, ProcCoordinator.Metrics procs, long acceptedImpacts, long omittedImpacts, int retainedParents) {}
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
        Fight(UUID id, UUID owner, TargetBackend backend, BoundingBox bounds, CombatEncounter combat, ProcCoordinator procs) {
            this.id = id; this.owner = owner; this.entityId = backend.entity().getUniqueId(); this.backend = backend; this.bounds = bounds.clone(); this.combat = combat; this.procs = procs;
            this.fire = new OwnedFireCoordinator(combat, 128);
        }
        boolean contains(Player player) { return player.getWorld().equals(backend.entity().getWorld()) && bounds.contains(player.getLocation().toVector()); }
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
    public ManagedCombatService(JavaPlugin plugin, OwnedBowService bows) { this.plugin = plugin; this.bows = bows; }
    public void start() {
        check();
        if (task != null) throw new IllegalStateException("Already started");
        bows.receiver(receiver);
        bows.retainedProtection(protection);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    /** Observation only, after production processing; it cannot replace the accounting receiver. */
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

    /** Shared control entry points must check before native allocation or persistence. */
    public void requireMutable() {
        check();
    }

    public void requireMutationAllowed() {
        mutation();
    }

    public void readOnlyNotification(Runnable notification) {
        mutation();
        notifying = true;
        try {
            notification.run();
        } finally {
            notifying = false;
        }
    }

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

    public Optional<View> owned(UUID owner) {
        thread(); return fights.values().stream().filter(fight -> fight.owner.equals(owner)).findFirst().map(Fight::view);
    }

    public Optional<View> view(UUID id) {
        thread(); Fight fight = fights.get(id); return Optional.ofNullable(fight == null ? history.get(id) : fight.view());
    }

    public Optional<Explanation> last(UUID owner) { thread(); return Optional.ofNullable(last.get(owner)); }
    public List<EncounterResult> completions() { thread(); return List.copyOf(completions.values()); }
    public int activeCount() { thread(); return fights.size(); }
    public OwnedFireCoordinator.Metrics fireMetrics(UUID encounter) {
        thread(); var fight = fights.get(encounter);
        return fight == null ? new OwnedFireCoordinator.Metrics(0, 0, 0) : fight.fire.metrics();
    }

    public int taskCount() { thread(); return task == null ? 0 : 1; }
    public boolean ownsEntity(UUID entity) { thread(); return fights.values().stream().anyMatch(fight -> fight.entityId.equals(entity)); }
    public void reset(UUID owner) {
        check();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.owner.equals(owner)) {
                terminate(fight);
            }
        }
    }

    /** Listener order cannot lose the old token: retain and compare exact activated sessions. */
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

    public void nativeDeathObserved(UUID entity, boolean cancelled) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity)) {
                fight.backend.deathObserved(cancelled);
            }
        }
    }

    public void nativeRemoved(UUID entity, EntityRemoveEvent.Cause cause) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity)) {
                fight.backend.removed(cause);
            }
        }
    }

    public void entityEnded(UUID entity) {
        mutation();
        for (Fight fight : List.copyOf(fights.values())) {
            if (fight.entityId.equals(entity) && fight.state == State.ACTIVE) {
                terminate(fight);
            }
        }
    }

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

    private void prune(Fight fight) {
        var needed = fight.procs.pendingParents();
        fight.shots.keySet().retainAll(needed);
        fight.collisions.keySet().retainAll(needed);
    }

    public List<String> diagnostics() { thread(); return List.copyOf(diagnostics); }
    private void diagnostic(String message) {
        diagnostics.addLast(message); while (diagnostics.size() > 32) diagnostics.removeFirst();
        plugin.getLogger().warning(message);
    }

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

    private Optional<DamageResult.RejectionReason> rejection(SettledHit hit) {
        return hit.rejection().map(reason -> switch (reason) {
            case PHYSICAL_VETO, NATIVE_VETO -> DamageResult.RejectionReason.CANCELLED;
            case ENCOUNTER_ENDED -> DamageResult.RejectionReason.ENCOUNTER_ENDED;
            case TARGET_DEAD -> DamageResult.RejectionReason.TARGET_DEAD;
            case OUTSIDE_ARENA -> DamageResult.RejectionReason.OUTSIDE_ARENA;
            case TARGET_CHANGED, UNSUPPORTED_PHASE -> DamageResult.RejectionReason.INVALID_TARGET;
        });
    }

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

    private static <K,V> void putBounded(LinkedHashMap<K,V> map, K key, V value, int capacity) {
        map.remove(key); map.put(key, value); while (map.size() > capacity) map.remove(map.keySet().iterator().next());
    }

    private static void tell(UUID owner, String message) {
        Player player = Bukkit.getPlayer(owner); if (player != null && player.isOnline()) player.sendMessage(Component.text(message));
    }

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

    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Managed combat requires server thread");
    }

    private void mutation() {
        thread();
        if (notifying) throw new IllegalStateException("Settled-hit observers are read-only");
    }

    private void check() {
        mutation();
        if (closed) throw new IllegalStateException("Managed combat closed");
    }

}
