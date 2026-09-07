package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.EncounterResult;
import com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import com.kaveenk.onlydragons.domain.encounter.definition.TrainingDragonSelection;
import com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;

/**
 * One configured development encounter, isolated by a private owner UUID from
 * player-owned practice fights. The existing {@link ManagedCombatService} owns
 * health/procs/results; this service owns configuration, generation selection and
 * read-only completion subscriptions. Native defeat and resource retirement are
 * distinct. Server-thread mutation is forbidden while combat observers run.
 * No countdown, ritual, real reward or persistent encounter recovery is implemented.
 */
public final class DevelopmentDragonService implements AutoCloseable {
    private final UUID owner = UUID.randomUUID();
    private final ManagedCombatService combat;
    private final DragonDefinitionRegistry definitions;
    private final ArenaConfiguration arena;
    private final ArenaTickets tickets;
    private UUID generation;
    private DragonBackend backend;
    private DragonCatalog.Selection selection;
    private SpawnMode spawnMode;
    /**
     * Once-only notification after frozen domain defeat and confirmed native death.
     * @param generation exact encounter generation, also the result's encounter ID
     * @param nativeId original native dragon UUID, retained through its animation
     * @param result immutable accounting, participant stamps and full selected catalog data
     * @param nativeOutcome observed backend outcome; ordinary notifications require ANIMATING
     */
    public record Completion(UUID generation, UUID nativeId, EncounterResult result, String nativeOutcome) {}
    /**
     * Removable synchronous completion registration; removal is idempotent and respects the combat read-only guard.
     */
    public interface Subscription extends AutoCloseable {
        /**
         * Removes only this registration; cannot be called from a read-only completion notification.
         */
        @Override void close();
    }

    /**
     * Identity-bearing subscriber so equal consumer objects remain separate registrations.
     * @param token unique registration identity
     * @param consumer read-only completion recipient; failures are isolated per recipient
     */
    private record Registration(UUID token, Consumer<Completion> consumer) {}
    private final List<Registration> subscribers = new ArrayList<>();
    private int notificationFailures;
    private boolean closed;
    private final DragonLeaderboardPresenter leaderboard;
    /**
     * Subscribes before defeat to the exact active generation, with a maximum of eight.
     * All registrations are removed before once-only delivery. A consumer must not mutate
     * combat/dragon state or unregister during notification; schedule later work instead.
     * @param expected exact current generation token
     * @param consumer non-null read-only recipient of the frozen completion
     * @return idempotent handle for removing this registration before delivery
     * @throws IllegalArgumentException for stale/nonactive/already-defeated generations
     * @throws IllegalStateException for wrong thread, closed/read-only service or capacity
     * @throws NullPointerException if consumer is null
     */
    public Subscription subscribe(UUID expected, Consumer<Completion> consumer) {
        check();
        requireGeneration(expected);
        if (!active() || view().orElseThrow().state() != ManagedCombatService.State.ACTIVE) {
            throw new IllegalArgumentException("Subscribe to an active generation before defeat.");
        }
        if (subscribers.size() >= 8) {
            throw new IllegalStateException("Dragon subscription capacity reached.");
        }
        var registration = new Registration(UUID.randomUUID(), Objects.requireNonNull(consumer));
        subscribers.add(registration);
        boolean[] removed = {false};
        return () -> {
            combat.requireMutationAllowed();
            if (!removed[0]) {
                subscribers.remove(registration);
                removed[0] = true;
            }
        };
    }

    /**
     * Reports currently registered completion consumers, including the ranking presenter.
     * @return registration count, zero after delivery/retirement
     * @throws IllegalStateException if called off the server thread
     */
    public int subscriberCount() {
        thread();
        return subscribers.size();
    }

    /**
     * Counts isolated subscriber exceptions without retrying delivered completions.
     * @return cumulative notification failure count
     * @throws IllegalStateException if called off the server thread
     */
    public int notificationFailures() {
        thread();
        return notificationFailures;
    }

    /**
     * Accepts only the current backend/generation and clears listeners before notification.
     * The shared combat guard prevents callback reentry from resetting live authority.
     * @param result already frozen domain completion from the current backend
     */
    private void completed(EncounterResult result) {
        if (closed || !Objects.equals(generation, result.encounterId()) || backend == null) {
            return;
        }
        var event = new Completion(generation, backend.entity().getUniqueId(), result, backend.outcome());
        var listeners = List.copyOf(subscribers);
        subscribers.clear();
        combat.readOnlyNotification(() -> {
            for (var listener : listeners) {
                try {
                    listener.consumer().accept(event);
                } catch (RuntimeException failure) {
                    notificationFailures++;
                }
            }
        });
    }

    /**
     * Binds existing gameplay/configuration/ticket owners and a production leaderboard sink.
     * @param combat plugin-lifetime accounting and observer guard
     * @param definitions catalog registry for new selections
     * @param arena explicit persisted development configuration
     * @param tickets shared plugin chunk-ticket broker
     */
    public DevelopmentDragonService(ManagedCombatService combat, DragonDefinitionRegistry definitions, ArenaConfiguration arena, ArenaTickets tickets) {
        this(combat, definitions, arena, tickets, new DragonLeaderboardPresenter());
    }
    /**
     * Binds the same lifecycle with an injected presentation boundary for behavior tests.
     * @param combat accounting and mutation guard
     * @param definitions trusted type catalog
     * @param arena persisted arena configuration
     * @param tickets shared native ticket owner
     * @param leaderboard non-null generation-scoped presenter; cannot supply accounting
     */
    DevelopmentDragonService(ManagedCombatService combat, DragonDefinitionRegistry definitions, ArenaConfiguration arena, ArenaTickets tickets, DragonLeaderboardPresenter leaderboard) {
        this.combat = combat;
        this.definitions = definitions;
        this.arena = arena;
        this.tickets = tickets;
        this.leaderboard = Objects.requireNonNull(leaderboard);
    }

    /**
     * Reads the last valid explicit configuration without creating/loading a world.
     * @return configured immutable cube, or empty before valid setup
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<DevelopmentArena> arena() {
        thread();
        return arena.current();
    }

    /**
     * Explains why initial arena configuration is unavailable.
     * @return empty for valid configuration, otherwise an operator diagnostic
     * @throws IllegalStateException if called off the server thread
     */
    public String arenaProblem() {
        thread();
        return arena.problem();
    }

    /**
     * Returns the most recent successful spawn token, including after retirement.
     * @return empty before any successful spawn; presence does not imply an active dragon
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<UUID> generation() {
        thread();
        return Optional.ofNullable(generation);
    }

    /**
     * Returns full captured definition/profile/table provenance for the latest spawn.
     * @return retained immutable selection, or empty before successful spawn
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<DragonCatalog.Selection> selection() {
        thread();
        return Optional.ofNullable(selection);
    }

    /**
     * Reads the current generation's live or bounded historical combat projection.
     * @return empty before spawn or after combat history eviction
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<ManagedCombatService.View> view() {
        thread();
        return generation == null ? Optional.empty() : combat.view(generation);
    }

    /**
     * Tests retained combat ownership, so native death animation still prevents overlapping spawns.
     * @return true while this control owner retains an active or terminal native backend
     */
    private boolean active() {
        return combat.owned(owner).isPresent();
    }

    /**
     * Persists a validated explicit arena only when no generation retains combat ownership.
     * No player-position fallback or live encounter migration occurs.
     * @param candidate complete cube and trusted type selection
     * @throws IllegalArgumentException for active ownership or invalid arena/type
     * @throws IllegalStateException for wrong thread, closed service or observer reentry
     * @throws IOException if persistence fails; failure is not reported as successful adoption
     */
    public void setup(DevelopmentArena candidate) throws IOException {
        check();
        if (active()) {
            throw new IllegalArgumentException("Reset the active dragon generation before setup.");
        }
        arena.save(candidate);
    }

    /**
     * Trusted HP/profile selection axis, independent of motion. STANDARD uses1000 HP
     * and level-based Tempo; TRAINING uses100000 HP under distinct selection revisions;
     * CALIBRATION retains the catalog's legacy profile and values.
     */
    public enum SpawnMode {
        STANDARD, TRAINING, CALIBRATION
    }

    /**
     * Spawns STANDARD through the backward-compatible stationary API default.
     * Human command routing chooses ORBIT explicitly and has a different motion default.
     * @return fresh successful encounter generation
     * @throws IllegalArgumentException if configuration/admission is invalid or already active
     * @throws IllegalStateException for wrong thread, closed service or observer reentry
     */
    public UUID spawn() {
        return spawn(SpawnMode.STANDARD);
    }

    /**
     * Spawns a selected health/profile mode with STATIONARY motion for API compatibility.
     * @param mode non-null trusted selection mode
     * @return fresh successful encounter generation
     * @throws IllegalArgumentException for invalid configuration/admission or existing ownership
     * @throws IllegalStateException for lifecycle/thread/observer violations
     * @throws NullPointerException if mode is null
     */
    public UUID spawn(SpawnMode mode) {
        return spawn(mode, DragonFlight.Mode.STATIONARY);
    }

    /**
     * Reads immutable route diagnostics without advancing the dragon.
     * @return retained backend motion state, or empty before backend adoption
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<DragonFlight.View> motion() {
        thread();
        return Optional.ofNullable(backend).map(DragonBackend::motion);
    }

    /**
     * Validates configuration, freezes a complete selection and opens the shared combat path.
     * Native construction/admission failure closes the candidate. Successful adoption
     * creates one ranking subscription; old presentation is retired before replacement.
     * @param mode non-null HP/profile selection, never a live HP edit
     * @param motion non-null native route mode, independent of HP selection
     * @return new generation used for all later reset/result/subscription calls
     * @throws IllegalArgumentException for invalid configuration, duplicate owner or rejected admission
     * @throws IllegalStateException for closed/read-only service, thread or native setup failure
     * @throws NullPointerException if either mode is null
     */
    public UUID spawn(SpawnMode mode, DragonFlight.Mode motion) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(motion);
        check();
        if (active()) {
            throw new IllegalArgumentException("Dragon already active; reset its generation first.");
        }
        var config = arena.current().orElseThrow(() -> new IllegalArgumentException(arena.problem()));
        var validated = config.validate(definitions.snapshot());
        var selected = mode == SpawnMode.CALIBRATION ? validated
                : TrainingDragonSelection.select(validated, mode == SpawnMode.TRAINING);
        DragonBackend candidate = new DragonBackend(config.location(), config.bounds(), motion, tickets, this::completed, value -> {
            if (backend == value) {
                retirePresentation();
            }
        });
        try {
            UUID id = combat.open(owner, candidate, config.bounds(), selected.maxHealth(), selected.defense(),
                    selected.identity().id(), selected.combatProfile(), Optional.of(selected), Math::random);
            retirePresentation();
            backend = candidate;
            generation = id;
            selection = selected;
            spawnMode = mode;
            leaderboard.begin(id);
            subscribe(id, leaderboard::accept);
            return id;
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
    }

    /**
     * Terminates only the exact current owned generation and retires its presentation.
     * No defeat result or reward is created by reset; already frozen diagnostics remain bounded.
     * @param expected exact generation token, not merely the current native entity UUID
     * @throws IllegalArgumentException for stale tokens or already retired ownership
     * @throws IllegalStateException for wrong thread, closed service or observer reentry
     */
    public void reset(UUID expected) {
        check();
        requireGeneration(expected);
        if (!active()) {
            throw new IllegalArgumentException("Generation already retired; no active dragon to reset.");
        }
        combat.reset(owner);
        retirePresentation();
    }

    /**
     * Reads an existing frozen defeat result without generating or announcing one.
     * @param expected exact latest generation token
     * @return combat view containing a nonempty immutable completion
     * @throws IllegalArgumentException for stale token, evicted history or no defeat result
     * @throws IllegalStateException if called off the server thread
     */
    public ManagedCombatService.View result(UUID expected) {
        thread();
        requireGeneration(expected);
        var view = view().orElseThrow(() -> new IllegalArgumentException("Generation history expired."));
        if (view.completion().isEmpty()) {
            throw new IllegalArgumentException("No frozen defeat result for this generation (" + view.state() + ").");
        }
        return view;
    }

    /**
     * Rejects stale caller tokens before any generation-specific action.
     * @param expected requested encounter UUID
     * @throws IllegalArgumentException unless it equals the latest successful generation
     */
    private void requireGeneration(UUID expected) {
        if (generation == null || !generation.equals(expected)) {
            throw new IllegalArgumentException("Unknown or stale dragon generation.");
        }
    }

    /**
     * Formats configuration, captured selection, native outcome and separate HP/credit totals.
     * This is a diagnostic projection, never state parsed back into combat authority.
     * @return non-null current/historical status with real rewards explicitly disabled
     * @throws IllegalStateException if called off the server thread
     */
    public String status() {
        thread();
        String config = arena.current().map(a -> "Arena " + a.worldKey() + " center=" + a.x() + "," + a.y() + "," + a.z()
                + " radius=" + a.radius() + " type=" + a.type()).orElse(arena.problem());
        var view = view();
        if (view.isEmpty()) {
            return config + " | dragon IDLE | rewards disabled";
        }
        var currentView = view.get();
        double hp = currentView.contributions().values().stream().mapToDouble(c -> c.actualHealthDamage()).sum();
        double credit = currentView.contributions().values().stream().mapToDouble(c -> c.contributionDamage()).sum();
        return config + " | mode=" + spawnMode.name().toLowerCase(Locale.ROOT) + " generation=" + generation + " native=" + currentView.entityId() + " state=" + currentView.state()
                + " nativePresent=" + !backend.released() + " nativeHP=" + backend.entity().getHealth()
                + " motion=" + backend.motion()
                + " animation=" + backend.entity().getDeathAnimationTicks() + " nativeOutcome=" + backend.outcome()
                + " | definition=" + selection.identity() + " catalog=" + selection.catalogIdentity()
                + " combat=" + selection.combatProfile().mechanic() + " phase=" + selection.phaseProfile().mechanic()
                + " table=" + selection.table().identity() + " | remainingHP=" + currentView.target().currentHealth()
                + " maxHP=" + currentView.target().maxHealth() + " HP removed=" + hp + " credit=" + credit + " | rewards disabled";
    }

    /**
     * Resets retained ownership and presentation once, then permanently closes controls.
     * Uses the shared mutation guard; consumer notifications cannot close the live service.
     * @throws IllegalStateException for wrong thread or read-only notification reentry
     */
    public void close() {
        combat.requireMutationAllowed();
        if (closed) {
            return;
        }
        if (active()) {
            combat.reset(owner);
        }
        retirePresentation();
        closed = true;
    }

    /**
     * Returns the latest frozen board; retirement does not rewrite its immutable ordering.
     * @return empty before a qualifying completion, otherwise the retained board
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<RankedEncounterResult> ranking() {
        thread();
        return leaderboard.ranking();
    }

    /**
     * Reports recipient-delivery failures separately from accounting/ranking validity.
     * @return cumulative isolated recipient failure count
     * @throws IllegalStateException if called off the server thread
     */
    public int leaderboardDeliveryFailures() {
        thread();
        return leaderboard.deliveryFailures();
    }

    /**
     * Drops all future subscriptions and invalidates current-generation delivery while retaining immutable ranking diagnostics.
     */
    private void retirePresentation() {
        subscribers.clear();
        if (generation != null) {
            leaderboard.retire(generation);
        }
    }

    /**
     * Enforces classic Paper server-thread control access; no Folia ownership is implied.
     */
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Dragon controls require server thread");
        }
    }

    /**
     * Rejects closed controls and combat-observer reentry before persistence or native allocation.
     */
    private void check() {
        thread();
        if (closed) {
            throw new IllegalStateException("Dragon controls closed");
        }
        combat.requireMutable();
    }
}
