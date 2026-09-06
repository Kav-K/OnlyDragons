package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.application.proc.ProcCoordinator;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        final Map<UUID, ProcCoordinator.Session> sessions = new HashMap<>();
        final Map<UUID, ShotContext> shots = new HashMap<>();
        final Map<UUID, Long> collisions = new HashMap<>();
        State state = State.ACTIVE;
        Fight(UUID id, UUID owner, TargetBackend backend, BoundingBox bounds, CombatEncounter combat, ProcCoordinator procs) {
            this.id = id; this.owner = owner; this.entityId = backend.entity().getUniqueId(); this.backend = backend; this.bounds = bounds.clone(); this.combat = combat; this.procs = procs;
        }
        boolean contains(Player p) { return p.getWorld().equals(backend.entity().getWorld()) && bounds.contains(p.getLocation().toVector()); }
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
    private final java.util.function.Predicate<UUID> protection = this::ownsEntity;
    private final Consumer<SettledHit> receiver = this::receive;
    private final EnchantEffects effects = EnchantEffects.checkpointTwo();
    private BukkitTask task;
    private boolean closed, notifying;
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();
    public ManagedCombatService(JavaPlugin plugin, OwnedBowService bows) { this.plugin = plugin; this.bows = bows; }
    public void start() {
        check(); if (task != null) throw new IllegalStateException("Already started");
        bows.receiver(receiver); bows.retainedProtection(protection); task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    /** Observation only, after production processing; it cannot replace the accounting receiver. */
    public Observation observeSettled(Consumer<SettledHit> observer) {
        check(); Objects.requireNonNull(observer);
        if (observers.size() >= 8) throw new IllegalStateException("Observer capacity reached");
        observers.add(observer); return () -> { mutation(); observers.remove(observer); };
    }
    /** Shared control entry points must check before native allocation or persistence. */
    public void requireMutable() { check(); }
    public void requireMutationAllowed() { mutation(); }
    public void readOnlyNotification(Runnable notification) {
        mutation(); notifying = true;
        try { notification.run(); } finally { notifying = false; }
    }
    public UUID open(UUID owner, TargetBackend backend, BoundingBox bounds, double hp, double defense,
                     String variant, CombatProfile profile, Optional<DragonCatalog.Selection> selection,
                     RandomSource random) {
        check(); Objects.requireNonNull(owner); Objects.requireNonNull(backend);
        if (fights.size() >= 16 || fights.values().stream().anyMatch(f -> f.owner.equals(owner)))
            throw new IllegalArgumentException("Reset your existing practice target first or wait for capacity.");
        UUID id = UUID.randomUUID();
        var combat = new CombatEncounter(new TargetState(id, UUID.randomUUID(), hp, hp, defense), variant, profile, selection);
        var procs = new ProcCoordinator(combat, new ProcCoordinator.Limits(4096, 256, 128, 2), random);
        var fight = new Fight(id, owner, backend, bounds, combat, procs);
        boolean admitted = false;
        try {
            bows.openEncounter(id, backend.entity().getWorld(), bounds, profile.mechanic()); admitted = true;
            bows.registerTarget(id, combat.target().targetId(), backend.entity());
            backend.synchronize(combat.target()); fights.put(id, fight); reconcile(fight);
            return id;
        } catch (RuntimeException failure) {
            fights.remove(id); procs.close(); if (admitted) bows.endEncounter(id); backend.close(); throw failure;
        }
    }
    public Optional<View> owned(UUID owner) {
        thread(); return fights.values().stream().filter(f -> f.owner.equals(owner)).findFirst().map(Fight::view);
    }
    public Optional<View> view(UUID id) {
        thread(); Fight f = fights.get(id); return Optional.ofNullable(f == null ? history.get(id) : f.view());
    }
    public Optional<Explanation> last(UUID owner) { thread(); return Optional.ofNullable(last.get(owner)); }
    public List<EncounterResult> completions() { thread(); return List.copyOf(completions.values()); }
    public int activeCount() { thread(); return fights.size(); }
    public int taskCount() { thread(); return task == null ? 0 : 1; }
    public boolean ownsEntity(UUID entity) { thread(); return fights.values().stream().anyMatch(f -> f.entityId.equals(entity)); }
    public void reset(UUID owner) {
        check(); for (Fight f : List.copyOf(fights.values())) if (f.owner.equals(owner)) terminate(f);
    }
    /** Listener order cannot lose the old token: retain and compare exact activated sessions. */
    public void playerEnded(UUID owner) {
        mutation(); last.remove(owner);
        for (Fight f : List.copyOf(fights.values())) {
            ProcCoordinator.Session old = f.sessions.remove(owner);
            if (old != null) f.procs.clearSession(old);
            reconcile(f);
        }
    }
    public void nativeDeathObserved(UUID entity, boolean cancelled) {
        mutation(); for (Fight f : List.copyOf(fights.values())) if (f.entityId.equals(entity)) f.backend.deathObserved(cancelled);
    }
    public void nativeRemoved(UUID entity, org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        mutation(); for (Fight f : List.copyOf(fights.values())) if (f.entityId.equals(entity)) f.backend.removed(cause);
    }
    public void entityEnded(UUID entity) {
        mutation(); for (Fight f : List.copyOf(fights.values()))
            if (f.entityId.equals(entity) && f.state == State.ACTIVE) terminate(f);
    }
    private void reconcile(Fight f) {
        if (f.state != State.ACTIVE) return;
        for (var old : List.copyOf(f.sessions.values())) {
            Player p = Bukkit.getPlayer(old.ownerId());
            if (!bows.isCurrentSession(old.ownerId(), old.token()) || p == null || !p.isOnline() || p.isDead() || !f.contains(p)) {
                f.procs.clearSession(old); f.sessions.remove(old.ownerId(), old);
            }
        }
        prune(f);
        for (Player p : Bukkit.getOnlinePlayers()) if (!p.isDead() && f.contains(p)) {
            bows.currentSession(p.getUniqueId()).ifPresent(token -> {
                var session = new ProcCoordinator.Session(p.getUniqueId(), token);
                if (!session.equals(f.sessions.get(p.getUniqueId())) && f.procs.activate(session)) f.sessions.put(p.getUniqueId(), session);
            });
        }
    }
    private void prune(Fight f) {
        var needed = f.procs.pendingParents(); f.shots.keySet().retainAll(needed); f.collisions.keySet().retainAll(needed);
    }
    public List<String> diagnostics() { thread(); return List.copyOf(diagnostics); }
    private void diagnostic(String message) {
        diagnostics.addLast(message); while (diagnostics.size() > 32) diagnostics.removeFirst();
        plugin.getLogger().warning(message);
    }
    private void receive(SettledHit hit) {
        if (closed) return;
        Fight f = fights.get(hit.impact().key().encounterId());
        if (f != null && f.state == State.ACTIVE) {
            try {
                reconcile(f);
                ShotContext shot = hit.projectile().shot();
                // The receiver's current commit tick is authoritative; collision remains separately retained.
                long now = Integer.toUnsignedLong(Bukkit.getCurrentTick());
                var impact = new PhysicalImpact(hit.impact().key(), hit.impact().ownerId(), now, hit.impact().position(), hit.impact().targetPart());
                var result = f.procs.physical(shot, impact, effects.modifiers(shot, impact.position(), f.backend.airborne()),
                        new ProcCoordinator.Session(shot.ownerId(), hit.projectile().sessionToken()), rejection(hit));
                if (!result.children().isEmpty()) {
                    f.shots.put(result.damage().impactId(), shot); f.collisions.put(result.damage().impactId(), hit.collisionTick());
                }
                explain(f, result.damage(), shot, hit.collisionTick(), result.admission());
                prune(f); synchronize(f);
            } catch (RuntimeException failure) { failed(f, failure); }
        }
        notifying = true;
        try {
            for (var observer : List.copyOf(observers)) {
                try { observer.accept(hit); }
                catch (RuntimeException failure) { diagnostic("Settled-hit observer failed: " + failure); }
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
        for (Fight f : List.copyOf(fights.values())) {
            try {
                if (f.state != State.ACTIVE) { if (f.backend.released()) release(f); continue; }
                if (!f.backend.entity().isValid() || f.backend.entity().isDead()) { terminate(f); continue; }
                reconcile(f);
                var drain = f.procs.tickOutcomes(now);
                for (var result : drain.results()) explain(f, result, f.shots.get(result.parentImpactId().orElse(result.impactId())),
                        f.collisions.getOrDefault(result.parentImpactId().orElse(result.impactId()), result.tick()), ProcCoordinator.Admission.NO_CHILDREN);
                prune(f);
                // Both parent and drain already mutated the domain. Never apply their damage again.
                synchronize(f);
                if (!drain.failures().isEmpty()) {
                    for (var failure : drain.failures()) tell(failure.command().ownerId(), "Combat proc failed: " + failure.reason()
                            + "; earlier committed HP/credit retained.");
                    if (f.state == State.ACTIVE) terminate(f);
                }
            } catch (RuntimeException failure) { failed(f, failure); }
        }
    }
    private void explain(Fight f, DamageResult damage, ShotContext shot, long collision, ProcCoordinator.Admission admission) {
        var total = f.combat.contributions().getOrDefault(damage.ownerId(), new EncounterResult.Contribution(0, 0, 0, false));
        var explanation = new Explanation(damage, shot, collision, admission, f.combat.target().currentHealth(), total);
        putBounded(last, damage.ownerId(), explanation, 128);
        Player player = Bukkit.getPlayer(damage.ownerId());
        if (player != null && f.contains(player)) {
            player.sendMessage(Component.text(explanation.summary())); player.sendActionBar(Component.text(explanation.summary()));
        }
    }
    private void synchronize(Fight f) {
        Optional<EncounterResult> result = f.combat.completion();
        if (result.isPresent() && f.state == State.ACTIVE) {
            // Publish immutable completion and close admission BEFORE native health zero/death can reenter.
            f.state = State.DEFEATED; f.procs.close(); f.sessions.clear();
            EncounterResult frozen = result.get();
            if (!completions.containsKey(frozen.completionId())) {
                putBounded(completions, frozen.completionId(), frozen, 64);
                if (f.backend.announcesImmediately()) frozen.participants().keySet().forEach(owner -> tell(owner, "Practice complete " + frozen.completionId()
                        + " | HP=" + frozen.participants().get(owner).actualHealthDamage()
                        + " credit=" + frozen.participants().get(owner).contributionDamage()));
            }
            f.backend.synchronize(f.combat.target());
            bows.endEncounter(f.id);
            f.backend.defeated(frozen);
            if (f.backend.released()) release(f);
        } else if (f.state == State.ACTIVE) f.backend.synchronize(f.combat.target());
    }
    private void failed(Fight f, RuntimeException failure) {
        // A callback/projection failure cannot erase a committed result or invite a retry.
        try { synchronize(f); }
        catch (RuntimeException recoveryFailure) { diagnostic("Recovery projection failed: " + recoveryFailure); }
        finally {
            tell(f.owner, "Practice stopped: " + failure.getMessage() + "; committed accounting retained.");
            terminate(f);
        }
    }
    private void terminate(Fight f) {
        if (!fights.containsKey(f.id)) return;
        if (f.state == State.ACTIVE) f.state = State.TERMINATED;
        // Every resource is attempted even when an extension backend fails.
        try { f.procs.close(); } catch (RuntimeException failure) { diagnostic("Proc cleanup failed: " + failure); }
        f.sessions.clear(); f.shots.clear(); f.collisions.clear();
        try { bows.endEncounter(f.id); } catch (RuntimeException failure) { diagnostic("Bow cleanup failed: " + failure); }
        try { f.backend.close(); } catch (RuntimeException failure) { diagnostic("Backend cleanup failed: " + failure); }
        finally { release(f); }
    }
    private void release(Fight f) {
        f.shots.clear(); f.collisions.clear();
        try { putBounded(history, f.id, f.view(), 32); }
        catch (RuntimeException failure) { diagnostic("History projection failed: " + failure); }
        finally { fights.remove(f.id); }
    }
    private static <K,V> void putBounded(LinkedHashMap<K,V> map, K key, V value, int capacity) {
        map.remove(key); map.put(key, value); while (map.size() > capacity) map.remove(map.keySet().iterator().next());
    }
    private static void tell(UUID owner, String message) {
        Player p = Bukkit.getPlayer(owner); if (p != null && p.isOnline()) p.sendMessage(Component.text(message));
    }
    public void close() {
        mutation(); if (closed) return;
        closed = true; if (task != null) task.cancel(); task = null;
        try {
            for (Fight f : List.copyOf(fights.values())) {
                try { terminate(f); } catch (RuntimeException failure) { diagnostic("Fight cleanup failed: " + failure); }
            }
        } finally {
            bows.clearReceiver(receiver); bows.clearRetainedProtection(protection); observers.clear(); history.clear(); last.clear(); completions.clear(); fights.clear();
        }
    }
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Managed combat requires server thread"); }
    private void mutation() { thread(); if (notifying) throw new IllegalStateException("Settled-hit observers are read-only"); }
    private void check() { mutation(); if (closed) throw new IllegalStateException("Managed combat closed"); }
}
