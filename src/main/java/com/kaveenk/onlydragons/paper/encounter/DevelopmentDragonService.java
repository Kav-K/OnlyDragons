package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;

/** One development encounter, isolated from each player's practice owner and generation. */
public final class DevelopmentDragonService implements AutoCloseable {
    private final UUID owner = UUID.randomUUID();
    private final ManagedCombatService combat;
    private final DragonDefinitionRegistry definitions;
    private final ArenaConfiguration arena;
    private final com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets;
    private UUID generation;
    private DragonBackend backend;
    private DragonCatalog.Selection selection;
    private SpawnMode spawnMode;
    public record Completion(UUID generation, UUID nativeId, com.kaveenk.onlydragons.domain.encounter.EncounterResult result, String nativeOutcome) {}
    public interface Subscription extends AutoCloseable { @Override void close(); }
    private record Registration(UUID token, java.util.function.Consumer<Completion> consumer) {}
    private final List<Registration> subscribers = new ArrayList<>();
    private int notificationFailures;
    private boolean closed;
    private final DragonLeaderboardPresenter leaderboard;
    public Subscription subscribe(UUID expected, java.util.function.Consumer<Completion> consumer) {
        check(); requireGeneration(expected);
        if (!active() || view().orElseThrow().state() != ManagedCombatService.State.ACTIVE) throw new IllegalArgumentException("Subscribe to an active generation before defeat.");
        if (subscribers.size() >= 8) throw new IllegalStateException("Dragon subscription capacity reached.");
        var registration = new Registration(UUID.randomUUID(), Objects.requireNonNull(consumer));
        subscribers.add(registration);
        boolean[] removed = {false};
        return () -> {
            combat.requireMutationAllowed();
            if (!removed[0]) { subscribers.remove(registration); removed[0] = true; }
        };
    }
    public int subscriberCount() { thread(); return subscribers.size(); }
    public int notificationFailures() { thread(); return notificationFailures; }
    private void completed(com.kaveenk.onlydragons.domain.encounter.EncounterResult result) {
        if (closed || !Objects.equals(generation, result.encounterId()) || backend == null) return;
        var event = new Completion(generation, backend.entity().getUniqueId(), result, backend.outcome());
        var listeners = List.copyOf(subscribers); subscribers.clear();
        combat.readOnlyNotification(() -> {
            for (var listener : listeners) {
                try { listener.consumer().accept(event); } catch (RuntimeException failure) { notificationFailures++; }
            }
        });
    }
    public DevelopmentDragonService(ManagedCombatService combat, DragonDefinitionRegistry definitions, ArenaConfiguration arena, com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets) {
        this(combat, definitions, arena, tickets, new DragonLeaderboardPresenter());
    }
    DevelopmentDragonService(ManagedCombatService combat, DragonDefinitionRegistry definitions, ArenaConfiguration arena, com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets, DragonLeaderboardPresenter leaderboard) {
        this.combat = combat; this.definitions = definitions; this.arena = arena; this.tickets = tickets;
        this.leaderboard = Objects.requireNonNull(leaderboard);
    }
    public Optional<DevelopmentArena> arena() { thread(); return arena.current(); }
    public String arenaProblem() { thread(); return arena.problem(); }
    public Optional<UUID> generation() { thread(); return Optional.ofNullable(generation); }
    public Optional<DragonCatalog.Selection> selection() { thread(); return Optional.ofNullable(selection); }
    public Optional<ManagedCombatService.View> view() { thread(); return generation == null ? Optional.empty() : combat.view(generation); }
    private boolean active() { return combat.owned(owner).isPresent(); }
    public void setup(DevelopmentArena candidate) throws IOException {
        check(); if (active()) throw new IllegalArgumentException("Reset the active dragon generation before setup.");
        arena.save(candidate);
    }
    public enum SpawnMode { STANDARD, TRAINING, CALIBRATION }
    public UUID spawn() { return spawn(SpawnMode.STANDARD); }
    /** Existing API consumers retain stationary targeting; commands select motion explicitly. */
    public UUID spawn(SpawnMode mode) { return spawn(mode, DragonFlight.Mode.STATIONARY); }
    public Optional<DragonFlight.View> motion() { thread(); return Optional.ofNullable(backend).map(DragonBackend::motion); }
    public UUID spawn(SpawnMode mode, DragonFlight.Mode motion) {
        Objects.requireNonNull(mode); Objects.requireNonNull(motion);
        check(); if (active()) throw new IllegalArgumentException("Dragon already active; reset its generation first.");
        var config = arena.current().orElseThrow(() -> new IllegalArgumentException(arena.problem()));
        var validated = config.validate(definitions.snapshot());
        var selected = mode == SpawnMode.CALIBRATION ? validated
                : com.kaveenk.onlydragons.domain.encounter.definition.TrainingDragonSelection.select(validated, mode == SpawnMode.TRAINING);
        DragonBackend candidate = new DragonBackend(config.location(), config.bounds(), motion, tickets, this::completed, value -> { if (backend == value) retirePresentation(); });
        try {
            UUID id = combat.open(owner, candidate, config.bounds(), selected.maxHealth(), selected.defense(),
                    selected.identity().id(), selected.combatProfile(), Optional.of(selected), Math::random);
            retirePresentation(); backend = candidate; generation = id; selection = selected; spawnMode = mode;
            leaderboard.begin(id); subscribe(id, leaderboard::accept); return id;
        } catch (RuntimeException failure) { candidate.close(); throw failure; }
    }
    public void reset(UUID expected) {
        check(); requireGeneration(expected);
        if (!active()) throw new IllegalArgumentException("Generation already retired; no active dragon to reset.");
        combat.reset(owner); retirePresentation();
    }
    public ManagedCombatService.View result(UUID expected) {
        thread(); requireGeneration(expected);
        var view = view().orElseThrow(() -> new IllegalArgumentException("Generation history expired."));
        if (view.completion().isEmpty()) throw new IllegalArgumentException("No frozen defeat result for this generation (" + view.state() + ").");
        return view;
    }
    private void requireGeneration(UUID expected) {
        if (generation == null || !generation.equals(expected)) throw new IllegalArgumentException("Unknown or stale dragon generation.");
    }
    public String status() {
        thread();
        String config = arena.current().map(a -> "Arena " + a.worldKey() + " center=" + a.x() + "," + a.y() + "," + a.z()
                + " radius=" + a.radius() + " type=" + a.type()).orElse(arena.problem());
        var view = view();
        if (view.isEmpty()) return config + " | dragon IDLE | rewards disabled";
        var v = view.get();
        double hp = v.contributions().values().stream().mapToDouble(c -> c.actualHealthDamage()).sum();
        double credit = v.contributions().values().stream().mapToDouble(c -> c.contributionDamage()).sum();
        return config + " | mode=" + spawnMode.name().toLowerCase(Locale.ROOT) + " generation=" + generation + " native=" + v.entityId() + " state=" + v.state()
                + " nativePresent=" + !backend.released() + " nativeHP=" + backend.entity().getHealth()
                + " motion=" + backend.motion()
                + " animation=" + backend.entity().getDeathAnimationTicks() + " nativeOutcome=" + backend.outcome()
                + " | definition=" + selection.identity() + " catalog=" + selection.catalogIdentity()
                + " combat=" + selection.combatProfile().mechanic() + " phase=" + selection.phaseProfile().mechanic()
                + " table=" + selection.table().identity() + " | remainingHP=" + v.target().currentHealth()
                + " maxHP=" + v.target().maxHealth() + " HP removed=" + hp + " credit=" + credit + " | rewards disabled";
    }
    public void close() { combat.requireMutationAllowed(); if (closed) return; if (active()) combat.reset(owner); retirePresentation(); closed = true; }
    public Optional<com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult> ranking() { thread(); return leaderboard.ranking(); }
    public int leaderboardDeliveryFailures() { thread(); return leaderboard.deliveryFailures(); }
    private void retirePresentation() {
        subscribers.clear();
        if (generation != null) leaderboard.retire(generation);
    }
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Dragon controls require server thread"); }
    private void check() { thread(); if (closed) throw new IllegalStateException("Dragon controls closed"); combat.requireMutable(); }
}
