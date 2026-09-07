package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CritResolver;
import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.enchant.OverloadCapture;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.projectile.ArrowRegistry;
import com.kaveenk.onlydragons.domain.projectile.FiringRules;
import com.kaveenk.onlydragons.domain.projectile.OwnedProjectile;
import com.kaveenk.onlydragons.domain.projectile.SettledHit;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import com.kaveenk.onlydragons.paper.projectile.homing.ArrowContinuity;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Sole owner of real arrows, accepted firing groups and first-collision claims.
 * All operations and returned live Bukkit objects belong to the classic Paper server
 * thread. Equipment is refreshed at fire admission, then immutable shot values survive
 * later swaps and session expiry. This class suppresses native arrow damage but never
 * computes combat damage or mutates target HP: one receiver consumes settled claims.
 * The first collision is terminal even when subsequently vetoed. Retirement precedes
 * receiver invocation, making reentrant accounting unable to claim the same arrow again.
 * @see OwnedBowListener
 * @see com.kaveenk.onlydragons.paper.encounter.ManagedCombatService
 * @see ArrowContinuity
 */
public final class OwnedBowService implements AutoCloseable {
    /**
     * Diagnostic retirement cause, not an accounting verdict; HIT includes rejected managed collisions.
     */
    public enum Retirement {
        HIT, MISS, FAILED_LAUNCH, REMOVED, ARENA_EXIT, OWNER_DEATH, RESET, DISABLE
    }

    /**
     * Bounded diagnostic journal entry; not a replacement for the immutable accounting ledger.
     * @param kind lifecycle operation
     * @param projectileId real native arrow UUID
     * @param ownerId captured shooter UUID
     * @param tick unsigned Bukkit tick at observation
     * @param detail operation-specific diagnostic text
     */
    public record Trace(String kind, UUID projectileId, UUID ownerId, long tick, String detail) {}
    /**
     * Immutable admission projection without a live World reference.
     * @param encounterId unique generation
     * @param worldId world identity
     * @param bounds admitted block-coordinate box
     * @param mechanic captured encounter policy revision
     */
    public record AdmittedArena(UUID encounterId, UUID worldId, TracerRules.Box bounds, MechanicRevision mechanic) {}
    /**
     * Immutable registered-target projection used to discover actual native dragon parts.
     * @param encounterId admitted generation
     * @param targetId logical accounting target
     * @param entityId live native parent identity
     * @param bounds enclosing admitted arena in block coordinates
     */
    public record AdmittedTarget(UUID encounterId, UUID targetId, UUID entityId, TracerRules.Box bounds) {}
    /**
     * Server-owned admission; copied bounds and exact World identity define membership.
     * @param id encounter generation
     * @param world Bukkit world owned by the server thread
     * @param bounds copied admission box
     * @param mechanic immutable policy revision
     */
    private record Arena(UUID id, World world, BoundingBox bounds, MechanicRevision mechanic) {
        /**
         * Checks world identity before applying Bukkit box containment.
         * @param location live block-coordinate position
         * @return true only inside this world and admitted box
         */
        boolean contains(Location location) {
            return world.equals(location.getWorld()) && bounds.contains(location.toVector());
        }
    }

    /**
     * Registration binds one logical target to its live native parent.
     * @param id logical target UUID
     * @param encounter admitted generation
     * @param entity native parent, read only on the server thread
     */
    private record Target(UUID id, UUID encounter, LivingEntity entity) {}
    /**
     * One queued native input; its final event result and captured identity are rechecked next tick.
     * @param event dispatch object retained to observe final item-use denial
     * @param session generation token at input
     * @param tick input tick
     * @param weapon complete item instance at input
     * @param slot selected zero-based hotbar slot
     */
    private record Input(PlayerInteractEvent event, UUID session, long tick, ItemInstance weapon, int slot) {}
    /**
     * Held-use identity; subsequent shots refresh stats without silently adopting a swapped item.
     * @param session live player token
     * @param weapon full captured item instance
     * @param slot selected zero-based hotbar slot
     */
    private record Hold(UUID session, ItemInstance weapon, int slot) {}
    /**
     * One provisional primary/optional Duplex child reservation and exactly-once ordinary-ammo charge. Native events remain referenced until later settlement so late launch cancellation is visible.
     */
    private static final class Group {
        final UUID id, owner, session;
        final Arena arena;
        final ItemStack ammo;
        final List<UUID> emitted = new ArrayList<>();
        EntityShootBowEvent bowEvent;
        ProjectileLaunchEvent launchEvent;
        OwnedProjectile primary;
        long due;
        int duplex;
        boolean accepted;
        boolean debitOutstanding;
        QuiverFlameProfile.Quiver quiver;
        boolean physicalRetirement;
        /**
         * Creates an unsettled reservation; a non-null ammo token represents a refundable debit.
         * @param id group identity
         * @param owner shooter UUID
         * @param session firing admission token
         * @param arena current admitted generation
         * @param ammo one ordinary arrow, or null when no survival debit exists
         */
        Group(UUID id, UUID owner, UUID session, Arena arena, ItemStack ammo) {
            this.id = id;
            this.owner = owner;
            this.session = session;
            this.arena = arena;
            this.ammo = ammo;
            this.debitOutstanding = ammo != null;
        }
    }

    /**
     * Immutable collision-time facts plus final-dispatch veto references. It does not remain an authority after retirement; accounting receives a SettledHit value.
     */
    private static final class Candidate {
        final OwnedProjectile owned;
        final ProjectileHitEvent event;
        final Target target;
        final UUID part;
        final long collisionTick;
        final Vector3 position;
        final boolean supportedPhase;
        boolean nativeVeto;
        /**
         * Captures physical position and phase before another event or tick can move the target.
         * @param owned immutable shot provenance
         * @param event real hit dispatch retained for its final cancellation
         * @param target exact registered target object
         * @param part native part UUID, or null for a parent/nonmultipart hit
         * @param tick actual collision tick
         */
        Candidate(OwnedProjectile owned, ProjectileHitEvent event, Target target, UUID part, long tick) {
            this.owned = owned;
            this.event = event;
            this.target = target;
            this.part = part;
            collisionTick = tick;
            position = vector(event.getEntity().getLocation().toVector());
            supportedPhase = phaseAllowed(target.entity());
        }
    }

    private final JavaPlugin plugin;
    private final EquipmentStatsService equipment;
    private final RandomSource random;
    private final ArrowRegistry registry;
    private final ArrowContinuity continuity;
    private final Map<UUID, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, UUID> sessions = new HashMap<>();
    private final Map<UUID, UUID> sessionArenas = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Arrow> entities = new HashMap<>();
    private final Map<UUID, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, Candidate> candidates = new LinkedHashMap<>();
    private final Map<UUID, Input> inputs = new LinkedHashMap<>();
    private final Map<UUID, Hold> holds = new HashMap<>();
    private final ArrayDeque<Trace> traces = new ArrayDeque<>();
    private Consumer<SettledHit> receiver;
    private Predicate<UUID> retainedProtection;
    private PlayerDeathEvent refundDeath;
    private BukkitTask task;
    private boolean closed;

    /**
     * Constructs the bounded registry and continuity broker; call start after wiring listeners/receiver.
     * @throws NullPointerException if a required collaborator is null
     * @throws IllegalArgumentException if capacity is invalid
     * @param plugin scheduler and ticket owner
     * @param equipment authoritative equipment refresher
     * @param capacity maximum combined emitted/reserved arrow slots, validated by ArrowRegistry
     * @param random source for captured crit, Overload and Quiver rolls
     */
    public OwnedBowService(JavaPlugin plugin, EquipmentStatsService equipment, int capacity, RandomSource random) {
        this.plugin = Objects.requireNonNull(plugin);
        this.equipment = Objects.requireNonNull(equipment);
        this.random = Objects.requireNonNull(random);
        registry = new ArrowRegistry(capacity);
        continuity = new ArrowContinuity(plugin);
    }

    /**
     * Starts the sole one-tick firing/claim/continuity task.
     * @throws IllegalStateException if off the server thread, closed or already started
     */
    public void start() {
        check();
        if (task != null) {
            throw new IllegalStateException("Already started");
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    /**
     * Admits a targetless firing arena after reserving its possible ticket footprint. Overlapping same-world boxes are rejected before adoption.
     * @throws IllegalArgumentException for duplicate, empty, overlapping or over-capacity admission
     * @throws IllegalStateException if closed or off the server thread
     * @param id unique generation UUID
     * @param world native world
     * @param bounds nonempty arena box, defensively copied
     * @param mechanic immutable mechanic revision
     */
    public void openEncounter(UUID id, World world, BoundingBox bounds, MechanicRevision mechanic) {
        check();
        Objects.requireNonNull(id);
        Objects.requireNonNull(world);
        Objects.requireNonNull(mechanic);
        if (arenas.containsKey(id) || bounds.getVolume() <= 0 || arenas.values().stream()
                .anyMatch(a -> a.world().equals(world) && a.bounds().overlaps(bounds)))
            throw new IllegalArgumentException("Duplicate/overlapping/empty arena");
        continuity.tickets().reserve(id, world, box(bounds));
        Arena arena = new Arena(id, world, bounds.clone(), mechanic);
        arenas.put(id, arena);
        Bukkit.getOnlinePlayers().stream().filter(p -> arena.contains(p.getLocation())).forEach(this::activate);
    }

    /**
     * Adds one valid living parent to an already admitted arena; does not create a combat engine.
     * @throws IllegalArgumentException for dead, invalid, outside or duplicate targets
     * @throws NullPointerException if the arena is not admitted
     * @param encounterId admitted generation
     * @param targetId unique logical target UUID
     * @param entity live native parent inside the arena
     */
    public void registerTarget(UUID encounterId, UUID targetId, LivingEntity entity) {
        check();
        Arena arena = requireArena(encounterId);
        if (!entity.isValid() || entity.isDead() || !arena.contains(entity.getLocation()) || targets.containsKey(entity.getUniqueId())
                || targets.values().stream().anyMatch(t -> t.id().equals(targetId)))
            throw new IllegalArgumentException("Invalid/duplicate target");
        targets.put(entity.getUniqueId(), new Target(Objects.requireNonNull(targetId), encounterId, entity));
    }

    /**
     * Removes native target registration without ending the arena or removing the entity.
     * @param entityId native parent UUID; absent registrations are harmless
     */
    public void unregisterTarget(UUID entityId) {
        check();
        targets.remove(entityId);
    }

    /**
     * Installs the single settled-claim consumer. Claims are terminal before this potentially reentrant callback runs.
     * @throws IllegalStateException if a receiver is already installed, closed or off-thread
     * @param receiver non-null accounting consumer
     */
    public void receiver(Consumer<SettledHit> receiver) {
        check();
        if (this.receiver != null) {
            throw new IllegalStateException("Receiver already registered");
        }
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Installs post-admission native-damage protection, such as a defeated dragon still animating.
     * @throws IllegalStateException if already installed, closed or off-thread
     * @param protection non-null predicate of native parent UUID
     */
    public void retainedProtection(Predicate<UUID> protection) {
        check();
        if (retainedProtection != null) {
            throw new IllegalStateException("Native protection already registered");
        }
        retainedProtection = Objects.requireNonNull(protection);
    }

    /**
     * Detaches only the exact installed predicate, allowing its owner to close without removing another owner.
     * @param protection installed predicate identity
     */
    public void clearRetainedProtection(Predicate<UUID> protection) {
        thread();
        if (retainedProtection == protection) {
            retainedProtection = null;
        }
    }

    /**
     * Detaches only the exact installed receiver; cleanup is permitted after service closure.
     * @param receiver installed callback identity
     */
    public void clearReceiver(Consumer<SettledHit> receiver) {
        thread();
        if (this.receiver == receiver) {
            this.receiver = null;
        }
    }

    /**
     * Looks up current firing admission without creating a new session.
     * @param owner player UUID
     * @return current token, or empty outside an active admission
     */
    public Optional<UUID> currentSession(UUID owner) {
        thread();
        return Optional.ofNullable(sessions.get(owner));
    }

    /**
     * Tests token equality without refreshing player membership.
     * @param owner player UUID
     * @param token captured token, possibly null
     * @return true only for a non-null current token
     */
    public boolean isCurrentSession(UUID owner, UUID token) {
        thread();
        return token.equals(sessions.get(owner));
    }

    /**
     * Snapshots emitted immutable shot records; callers cannot mutate the registry through this list.
     * @return current immutable projectile values
     */
    public List<OwnedProjectile> projectiles() {
        thread();
        return registry.snapshot();
    }

    /**
     * Finds immutable provenance while an arrow remains registered.
     * @param id native arrow UUID
     * @return registered value, or empty after retirement
     */
    public Optional<OwnedProjectile> projectile(UUID id) {
        thread();
        return registry.lookup(id);
    }

    /**
     * Exposes a live native arrow for server-thread observation; ownership and retirement remain here.
     * @param id native arrow UUID
     * @return owned entity reference, or empty when absent
     */
    public Optional<Arrow> arrow(UUID id) {
        thread();
        return Optional.ofNullable(entities.get(id));
    }

    /**
     * Snapshots target-independent admissions for cooperating adapters.
     * @return immutable arena value list
     */
    public List<AdmittedArena> admittedArenas() {
        thread();
        return arenas.values().stream().map(a -> new AdmittedArena(a.id(), a.world().getUID(), box(a.bounds()), a.mechanic())).toList();
    }

    /**
     * Snapshots currently registered native parents and their arena bounds.
     * @param encounter admitted generation UUID
     * @return immutable target projections for continuity acquisition
     */
    public List<AdmittedTarget> admittedTargets(UUID encounter) {
        thread();
        Arena arena = arenas.get(encounter);
        if (arena == null) {
            return List.of();
        }
        return targets.values().stream().filter(t -> t.encounter().equals(encounter))
                .map(t -> new AdmittedTarget(encounter, t.id(), t.entity().getUniqueId(), box(arena.bounds()))).toList();
    }

    /**
     * Returns the shared continuity/ticket owner; consumers must use distinct demand identities.
     * @return service-owned continuity adapter, never a new task
     */
    public ArrowContinuity continuity() {
        thread();
        return continuity;
    }

    /**
     * Reads combined registry occupancy for admission diagnostics.
     * @return emitted plus reserved arrow slots
     */
    public int capacityUsed() {
        thread();
        return registry.used();
    }

    /**
     * Reads capacity promised to unsettled groups.
     * @return reserved arrow slots
     */
    public int reservedCapacity() {
        thread();
        return registry.reserved();
    }

    /**
     * Counts firing groups awaiting final launch/ammo settlement.
     * @return number of unsettled groups
     */
    public int pendingGroups() {
        thread();
        return groups.size();
    }

    /**
     * Counts first collisions waiting for final-dispatch veto inspection.
     * @return number of unsettled physical claims
     */
    public int pendingClaims() {
        thread();
        return candidates.size();
    }

    /**
     * Reports ownership of the single scheduler task.
     * @return one while a task handle exists, otherwise zero
     */
    public int taskCount() {
        thread();
        return task == null ? 0 : 1;
    }

    /**
     * Copies the bounded lifecycle journal; at most 512 newest entries are retained.
     * @return immutable chronological diagnostic list
     */
    public List<Trace> trace() {
        thread();
        return List.copyOf(traces);
    }

    /**
     * Reconciles current physical arena membership, replacing stale admission only for a living player inside an admitted box.
     * @param player current native player
     */
    public void activate(Player player) {
        check();
        UUID owner = player.getUniqueId();
        Arena current = arena(player.getLocation());
        UUID previous = sessions.get(owner);
        if (previous != null && (current == null || !current.id().equals(sessionArenas.get(owner))))
            clearSession(owner, previous, false);
        if (current != null && !player.isDead()) {
            sessions.computeIfAbsent(owner, ignored -> UUID.randomUUID());
            sessionArenas.put(owner, current.id());
        }
    }

    /**
     * Invalidates only the matching generation. Quit/leave cancels pending children while retaining an already launched primary and its charge; death can remove all matching airborne arrows.
     * @param owner player UUID
     * @param token expected session generation
     * @param removeAirborne true for destructive owner-death cleanup, false for quit/leave
     */
    public void clearSession(UUID owner, UUID token, boolean removeAirborne) {
        check();
        if (!sessions.remove(owner, token)) {
            return;
        }
        sessionArenas.remove(owner);
        cooldowns.remove(owner);
        inputs.remove(owner);
        holds.remove(owner);
        for (Group group : List.copyOf(groups.values())) {
            if (group.owner.equals(owner) && group.session.equals(token)) {
                if (!removeAirborne && primaryLaunched(group)) {
                    cancelDelayed(group);
                } else {
                    fail(group);
                }
            }
        }
        if (removeAirborne) {
            for (var arrow : registry.snapshot()) {
                if (arrow.shot().ownerId().equals(owner)) {
                    retire(arrow.shot().projectileId(), Retirement.OWNER_DEATH);
                }
            }
        }
    }

    /**
     * Cleans every arrow/group of the dead owner even if arena departure already removed its session. Refunds enter death drops when inventory is not kept.
     * @param event real player-death event used as the refund destination
     */
    void ownerDied(PlayerDeathEvent event) {
        check();
        refundDeath = event;
        try {
            UUID owner = event.getPlayer().getUniqueId();
            currentSession(owner).ifPresent(token -> clearSession(owner, token, true));
            // Arena exit/quit can already have invalidated the session while preserving airborne ownership.
            // Death retires that ownership independently of whether a live token still exists.
            for (Group group : List.copyOf(groups.values())) {
                if (group.owner.equals(owner)) {
                    fail(group);
                }
            }
            for (var arrow : registry.snapshot()) {
                if (arrow.shot().ownerId().equals(owner)) {
                    retire(arrow.shot().projectileId(), Retirement.OWNER_DEATH);
                }
            }
        } finally {
            refundDeath = null;
        }
    }

    /**
     * Ends firing admission if this native parent is still registered.
     * @param entity actual dead parent
     */
    void targetDied(LivingEntity entity) {
        check();
        Target target = targets.get(entity.getUniqueId());
        if (target != null) {
            endEncounter(target.encounter());
        }
    }

    /**
     * Retires this arena's groups/arrows, sessions, targets and broker reservation. Retained native-protection ownership is separately managed.
     * @param id generation UUID; absent admissions are harmless
     */
    public void endEncounter(UUID id) {
        check();
        arenas.remove(id);
        targets.values().removeIf(t -> t.encounter().equals(id));
        for (Group group : List.copyOf(groups.values())) {
            if (group.arena.id().equals(id)) {
                fail(group);
            }
        }
        for (var arrow : registry.snapshot()) {
            if (arrow.shot().encounterId().equals(id)) {
                retire(arrow.shot().projectileId(), Retirement.RESET);
            }
        }
        for (UUID owner : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(owner);
            if (id.equals(sessionArenas.get(owner))) {
                clearSession(owner, sessions.get(owner), false);
            }
        }
        continuity.tickets().endArena(id);
    }

    /**
     * Admits a real main-hand ordinary-bow release at HIGHEST after refreshed identity, force, permission and prior-cancellation checks. Shortbow native release is suppressed; accepted release is rechecked next tick.
     * @param event native bow event, including cancelled events
     */
    void drawn(EntityShootBowEvent event) {
        check();
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Arena arena = arena(player.getLocation());
        if (arena == null) {
            return;
        }
        var inspection = equipment.refresh(player);
        ItemReadResult hand = event.getHand() == EquipmentSlot.HAND ? inspection.fingerprint().mainHand() : inspection.fingerprint().offHand();
        if (!(hand instanceof ItemReadResult.Valid valid)) {
            return;
        }
        // T01b defines only main-hand offense. Shortbow native release must never create a second primary.
        if (event.getHand() != EquipmentSlot.HAND || valid.item().resolvedWeapon().firingMode() == WeaponDefinition.FiringMode.SHORTBOW) {
            event.setCancelled(true);
            refundNative(player, event.getConsumable(), event.getBow());
            return;
        }
        if (event.isCancelled()) {
            refundNative(player, event.getConsumable(), event.getBow());
            return;
        }
        if (!player.hasPermission("onlydragons.fire") || !(event.getProjectile() instanceof Arrow arrow)
                || event.getForce() <= 0 || !Objects.equals(event.getBow(), player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            refundNative(player, event.getConsumable(), event.getBow());
            return;
        }
        Group group = reserve(player, arena, valid.item(), event.getConsumable(), true);
        if (group == null) {
            event.setCancelled(true);
            refundNative(player, event.getConsumable(), event.getBow());
            return;
        }
        try {
            group.bowEvent = event;
            group.primary = capture(player, arrow, group, valid.item(), inspection, event.getForce());
            own(arrow, group.primary, group);
            group.due = now() + 1;
        } catch (RuntimeException failure) {
            event.setCancelled(true);
            fail(group);
            throw failure;
        }
    }

    /**
     * Captures the primary launch event for later veto inspection and clears native damage/fire/critical effects on owned arrows.
     * @param event native projectile launch event
     */
    void launched(ProjectileLaunchEvent event) {
        check();
        var owned = registry.lookup(event.getEntity().getUniqueId());
        if (owned.isEmpty()) {
            return;
        }
        Group group = groups.get(owned.get().shot().shotId());
        if (group != null && owned.get().shot().ordinal() == 0) {
            group.launchEvent = event;
        }
        suppress((Arrow) event.getEntity());
    }

    /**
     * Queues one captured main-hand shortbow input per player; does not cancel or claim native interaction authority.
     * @param event real interaction whose final item-use result is inspected next tick
     */
    void interact(PlayerInteractEvent event) {
        check();
        if (event.getHand() != EquipmentSlot.HAND || arena(event.getPlayer().getLocation()) == null) {
            return;
        }
        var inspection = equipment.refresh(event.getPlayer());
        if (!(inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid)
                || valid.item().resolvedWeapon().firingMode() != WeaponDefinition.FiringMode.SHORTBOW) return;
        activate(event.getPlayer());
        // Keep the final event object for next-tick veto. Do not poison its cancellation state ourselves.
        inputs.putIfAbsent(event.getPlayer().getUniqueId(), new Input(event, sessions.get(event.getPlayer().getUniqueId()), now(),
                valid.item().instance(), event.getPlayer().getInventory().getHeldItemSlot()));
    }

    /**
     * Drops queued/held use immediately while preserving cooldowns and independently owned airborne groups.
     * @param owner player UUID
     */
    void stopUsing(UUID owner) {
        check();
        inputs.remove(owner);
        holds.remove(owner);
    }

    /**
     * Claims the first real collision at LOWEST. Misses retire immediately; a managed collision waits one tick for final cancellation and phase/target validation.
     * @param event native physical hit event
     */
    void hit(ProjectileHitEvent event) {
        check();
        UUID id = event.getEntity().getUniqueId();
        var owned = registry.claim(id);
        if (owned.isEmpty()) {
            return;
        }
        Entity hit = event.getHitEntity();
        LivingEntity parent = hit instanceof EnderDragonPart part ? part.getParent() : hit instanceof LivingEntity living ? living : null;
        Target target = parent == null ? null : targets.get(parent.getUniqueId());
        if (target == null || !target.encounter().equals(owned.get().shot().encounterId())) {
            retire(id, Retirement.MISS);
            return;
        }
        candidates.put(id, new Candidate(owned.get(), event, target, hit instanceof EnderDragonPart ? hit.getUniqueId() : null, now()));
        record("claim", owned.get(), "target=" + target.id());
    }

    /**
     * Records cancellation already present before this listener as an additional veto, then suppresses native damage for every owned arrow. Later arbitrary cancellation setters are not attributed to external plugins.
     * @param event native entity damage dispatch
     */
    void damage(EntityDamageByEntityEvent event) {
        check();
        if (!(event.getDamager() instanceof Arrow arrow) || registry.lookup(arrow.getUniqueId()).isEmpty()) {
            return;
        }
        Candidate candidate = candidates.get(arrow.getUniqueId());
        if (candidate != null && event.isCancelled()) {
            candidate.nativeVeto = true;
        }
        // All owned native damage is inert, including unrelated targets: no second combat authority.
        event.setDamage(0);
        event.setCancelled(true);
    }

    /**
     * Suppresses native HP damage on admitted or retained managed parents, including multipart hits; this cancellation is not itself physical-hit acceptance.
     * @param event native damage event of any cause
     */
    void protectTarget(EntityDamageEvent event) {
        check();
        Entity entity = event.getEntity();
        UUID id = entity instanceof EnderDragonPart part ? part.getParent().getUniqueId() : entity.getUniqueId();
        if (targets.containsKey(id) || (retainedProtection != null && retainedProtection.test(id))) {
            event.setDamage(0);
            event.setCancelled(true);
        }
    }

    /**
     * Reconciles sessions, due groups, final physical claims, queued/held input and native continuity in that order. Snapshot iteration permits synchronous lifecycle callbacks to remove ownership safely.
     */
    private void tick() {
        if (closed) {
            return;
        }
        long tick = now();
        // Membership must settle before any delayed emission, including direct A-to-B movement.
        for (UUID owner : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || player.isDead()) {
                clearSession(owner, sessions.get(owner), player != null);
            } else {
                activate(player);
            }
        }
        for (Group group : List.copyOf(groups.values())) {
            if (group.due <= tick) {
                settleGroup(group);
            }
        }
        for (Candidate candidate : List.copyOf(candidates.values())) {
            if (candidate.collisionTick < tick) {
                settle(candidate);
            }
        }
        for (var entry : List.copyOf(inputs.entrySet())) {
            Input input = entry.getValue();
            if (input.tick() >= tick || inputs.get(entry.getKey()) != input) {
                continue;
            }
            try {
                Player player = input.event().getPlayer();
                if (isCurrentSession(entry.getKey(), input.session()) && input.event().useItemInHand() != Event.Result.DENY
                        && sameInputWeapon(player, input.weapon(), input.slot())) {
                    shortbow(player);
                    // Launch callbacks can stop this press or end its session. Never recreate that authorization.
                    if (inputs.get(entry.getKey()) == input && isCurrentSession(entry.getKey(), input.session())
                            && sameInputWeapon(player, input.weapon(), input.slot())
                            && input.event().getAction().isRightClick() && player.isHandRaised()
                            && player.getHandRaised() == EquipmentSlot.HAND) {
                        holds.put(entry.getKey(), new Hold(input.session(), input.weapon(), input.slot()));
                    }
                }
            } finally {
                inputs.remove(entry.getKey(), input);
            }
        }
        for (var entry : List.copyOf(holds.entrySet())) {
            if (holds.get(entry.getKey()) != entry.getValue()) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            Hold hold = entry.getValue();
            if (player == null || !player.isHandRaised() || player.getHandRaised() != EquipmentSlot.HAND
                    || !isCurrentSession(entry.getKey(), hold.session())) {
                holds.remove(entry.getKey());
                continue;
            }
            if (!sameInputWeapon(player, hold.weapon(), hold.slot())) {
                holds.remove(entry.getKey());
                continue;
            }
            shortbow(player);
        }
        for (var owned : registry.snapshot()) {
            UUID id = owned.shot().projectileId();
            if (candidates.containsKey(id)) {
                continue;
            }
            Arrow arrow = entities.get(id);
            Arena arena = arenas.get(owned.shot().encounterId());
            if (arrow == null || !arrow.isValid() || arrow.isDead()) {
                retire(id, Retirement.REMOVED);
            } else if (arena == null || !arena.contains(arrow.getLocation())) {
                retire(id, Retirement.ARENA_EXIT);
            } else if (arrow.isInBlock() || arrow.isOnGround()) {
                retire(id, Retirement.MISS);
            } else {
                String previous = continuity.frame(id).flatMap(f -> f.aim()).map(a -> a.part().targetId() + ":" + a.part().partId()).orElse("");
                continuity.tick(owned, arrow, admittedTargets(owned.shot().encounterId()), tick);
                var aim = continuity.frame(id).orElseThrow().aim();
                String current = aim.map(a -> a.part().targetId() + ":" + a.part().partId()).orElse("");
                if (!previous.equals(current)) {
                    if (!previous.isEmpty()) {
                        record("tracer-released", owned, previous);
                    }
                    if (!current.isEmpty()) {
                        record("tracer-acquired", owned, owned.tracerProfile().revision() + " target:part=" + current + " distance=" + aim.orElseThrow().distance());
                    }
                }
            }
        }
    }

    /**
     * Refreshes equipment and compares the full item instance plus selected slot before reusing held input.
     * @param player current native player
     * @param weapon captured complete instance
     * @param slot captured hotbar slot
     * @return true only for the same valid current item and slot
     */
    private boolean sameInputWeapon(Player player, ItemInstance weapon, int slot) {
        return player.getInventory().getHeldItemSlot() == slot
                && equipment.refresh(player).fingerprint().mainHand() instanceof ItemReadResult.Valid valid
                && weapon.equals(valid.item().instance());
    }

    /**
     * Attempts one public-API native launch after current permission, membership, cooldown, ammo and equipment checks. Cooldown advances only after a valid uncancelled launch.
     * @param player current firing player
     */
    private void shortbow(Player player) {
        if (!player.isOnline() || player.isDead() || !player.hasPermission("onlydragons.fire")) {
            return;
        }
        Arena arena = arena(player.getLocation());
        if (arena == null || now() < cooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        var inspection = equipment.refresh(player);
        if (!(inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid)
                || valid.item().resolvedWeapon().firingMode() != WeaponDefinition.FiringMode.SHORTBOW) return;
        Group group = reserve(player, arena, valid.item(), new ItemStack(Material.ARROW), false);
        if (group == null) {
            return;
        }
        try {
            Vector velocity = player.getEyeLocation().getDirection().multiply(3);
            Arrow arrow = player.launchProjectile(Arrow.class, velocity, created -> {
                created.setVelocity(velocity);
                group.primary = capture(player, created, group, valid.item(), inspection, 1);
                own(created, group.primary, group);
            });
            if (!arrow.isValid() || group.launchEvent != null && group.launchEvent.isCancelled()) {
                fail(group);
                return;
            }
            cooldowns.put(player.getUniqueId(), now() + FiringRules.cooldown(inspection.stats().snapshot().effective(StatKey.ATTACK_SPEED)));
            group.due = now() + 1;
        } catch (RuntimeException failure) {
            fail(group);
            throw failure;
        }
    }

    /**
     * Reserves primary/child capacity before recording a survival charge. Native bows supply the debit token; shortbows remove one matching ordinary arrow here.
     * @param player current shooter
     * @param arena admitted generation
     * @param item resolved captured weapon
     * @param consumable native consumable or ordinary-arrow template
     * @param nativeDebit true when native bow processing owns consumption
     * @return unsettled group, or null when admission, capacity or ammo fails
     */
    private Group reserve(Player player, Arena arena, ItemRegistry.ResolvedItem item, ItemStack consumable, boolean nativeDebit) {
        activate(player);
        UUID token = sessions.get(player.getUniqueId());
        if (token == null) {
            return null;
        }
        int duplex = item.enchantments().stream().filter(e -> e.id().equals("duplex")).mapToInt(WeaponDefinition.Enchantment::level).findFirst().orElse(0);
        UUID id = UUID.randomUUID();
        if (!registry.reserve(id, duplex == 0 ? 1 : 2)) {
            feedback(player, "Arrow capacity reached.");
            return null;
        }
        ItemStack ammo = null;
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (consumable == null || consumable.getType() != Material.ARROW) {
                registry.release(id);
                return null;
            }
            if (nativeDebit) {
                ammo = consumable.asQuantity(1);
            } else {
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack != null && stack.isSimilar(consumable) && stack.getAmount() > 0) {
                        ammo = stack.asQuantity(1);
                        player.getInventory().setItem(slot, stack.asQuantity(stack.getAmount() - 1));
                        break;
                    }
                }
            }
            if (ammo == null) {
                registry.release(id);
                feedback(player, "An ordinary arrow is required.");
                return null;
            }
        }
        Group group = new Group(id, player.getUniqueId(), token, arena, ammo);
        group.duplex = duplex;
        if (nativeDebit && nativeInfinity(player.getInventory().getItemInMainHand(), consumable)) {
            group.debitOutstanding = false;
        }
        groups.put(id, group);
        return group;
    }

    /**
     * Freezes gear, stats, rolls, session and Tracer profile once for the physical group; descendants inherit the captured primary provenance.
     * @param player shooter
     * @param arrow actual native primary
     * @param group reserved group receiving the Quiver roll
     * @param item resolved weapon snapshot
     * @param inspection accepted-fire stats snapshot
     * @param force native draw force or full shortbow force
     * @return immutable primary provenance
     */
    private OwnedProjectile capture(Player player, Arrow arrow, Group group, ItemRegistry.ResolvedItem item,
            EquipmentStatsService.Inspection inspection, double force) {
        ShotContext shot = new ShotContext(group.arena.id(), group.id, arrow.getUniqueId(), 0, Optional.empty(),
                player.getUniqueId(), item.instance().identity(), inspection.stats().snapshot(), item.enchantments(),
                group.arena.mechanic(), now(), vector(arrow.getLocation().toVector()), vector(arrow.getVelocity()),
                new CritResolver().roll(inspection.stats().snapshot(), random), force, 1,
                OverloadCapture.roll(
                        EnchantEffects.level(item.enchantments(), "overload", 5),
                        inspection.stats().snapshot(), random));
        group.quiver = QuiverFlameProfile.capture(
                EnchantEffects.level(item.enchantments(), "infinite_quiver", 10), random);
        return new OwnedProjectile(shot, group.session,
                TracerProfile.forDefinition(item.definition().weapon()), shot.launchTick());
    }

    /**
     * Registers one actual entity and removes native damage, pickup and persistence authority before later settlement.
     * @param arrow native entity
     * @param owned immutable matching provenance
     * @param group reservation collecting emitted UUIDs
     */
    private void own(Arrow arrow, OwnedProjectile owned, Group group) {
        suppress(arrow);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setPersistent(false);
        registry.emit(owned);
        entities.put(arrow.getUniqueId(), arrow);
        group.emitted.add(arrow.getUniqueId());
        record("emitted", owned, "parent=" + owned.shot().parentProjectileId().map(UUID::toString).orElse("primary"));
    }

    /**
     * Clears native arrow damage, critical and fire effects; plugin accounting remains the only managed damage authority.
     * @param arrow owned native entity
     */
    private static void suppress(Arrow arrow) {
        arrow.setDamage(0);
        arrow.setCritical(false);
        arrow.setFireTicks(0);
    }

    /**
     * Observes final launch events and live-session admission before creating at most one delayed Duplex child at the captured launch transform. Success settles one shared ammo charge; failure retires/refunds the group.
     * @param group due provisional group
     */
    private void settleGroup(Group group) {
        if (!groups.containsKey(group.id)) {
            return;
        }
        Player owner = Bukkit.getPlayer(group.owner);
        if (owner == null || owner.isDead() || !group.arena.contains(owner.getLocation())
                || arenas.get(group.arena.id()) != group.arena || !isCurrentSession(group.owner, group.session)
                || launchVetoed(group)) {
            fail(group);
            return;
        }
        Arrow primary = entities.get(group.primary.shot().projectileId());
        if (!group.physicalRetirement && (primary == null || !primary.isValid())) {
            fail(group);
            return;
        }
        if (primary != null) {
            suppress(primary);
        }
        if (group.duplex > 0) {
            try {
                ShotContext shot = group.primary.shot();
                Location location = new Location(group.arena.world(), shot.launchPosition().x(), shot.launchPosition().y(), shot.launchPosition().z());
                if (!group.arena.contains(location) || !group.arena.world().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    fail(group);
                    return;
                }
                Arrow child = group.arena.world().spawn(location, Arrow.class, created -> {
                    created.setShooter(Bukkit.getPlayer(group.owner));
                    created.setVelocity(new Vector(shot.initialVelocity().x(), shot.initialVelocity().y(), shot.initialVelocity().z()));
                    own(created, group.primary.child(FiringRules.child(shot, created.getUniqueId(), now(), group.duplex)), group);
                });
                if (!child.isValid()) {
                    fail(group);
                    return;
                }
            } catch (RuntimeException failure) {
                fail(group);
                throw failure;
            }
        }
        acceptAmmo(group);
        registry.release(group.id);
        groups.remove(group.id);
        record("accepted-group", group.primary, "children=" + (group.duplex > 0 ? 1 : 0));
    }

    /**
     * Builds a value claim with collision and actual settlement ticks, retires ownership, then invokes the single receiver even for explicit rejection.
     * @param candidate first collision retained through completed native dispatch
     */
    private void settle(Candidate candidate) {
        UUID id = candidate.owned.shot().projectileId();
        if (!candidates.containsKey(id)) {
            return;
        }
        Optional<SettledHit.Rejection> rejection = rejection(candidate);
        PhysicalImpact impact = new PhysicalImpact(new PhysicalImpact.Key(candidate.owned.shot().encounterId(), id, candidate.target.id(), 0),
                candidate.owned.shot().ownerId(), now(), candidate.position, Optional.ofNullable(candidate.part).map(UUID::toString));
        SettledHit settled = new SettledHit(candidate.owned, impact, candidate.collisionTick, now(), rejection);
        retire(id, Retirement.HIT);
        // Terminal before invoking a potentially reentrant/throwing receiver.
        record("settled", candidate.owned, rejection.map(Enum::name).orElse("ACCEPTED"));
        if (receiver != null) {
            receiver.accept(settled);
        }
    }

    /**
     * Checks physical cancellation before pre-own native veto, generation/target identity, both collision/current phases and current arena membership.
     * @param candidate captured collision plus final event state
     * @return first explicit rejection, or empty for physical acceptance
     */
    private Optional<SettledHit.Rejection> rejection(Candidate candidate) {
        if (candidate.event.isCancelled()) {
            return Optional.of(SettledHit.Rejection.PHYSICAL_VETO);
        }
        if (candidate.nativeVeto) {
            return Optional.of(SettledHit.Rejection.NATIVE_VETO);
        }
        Arena arena = arenas.get(candidate.owned.shot().encounterId());
        if (arena == null) {
            return Optional.of(SettledHit.Rejection.ENCOUNTER_ENDED);
        }
        if (candidate.target.entity().isDead() || !candidate.target.entity().isValid()) {
            return Optional.of(SettledHit.Rejection.TARGET_DEAD);
        }
        if (targets.get(candidate.target.entity().getUniqueId()) != candidate.target
                || candidate.part != null && (!(candidate.event.getHitEntity() instanceof EnderDragonPart part)
                || !part.getParent().equals(candidate.target.entity()) || candidate.target.entity() instanceof EnderDragon dragon
                && dragon.getParts().stream().noneMatch(p -> p.getUniqueId().equals(candidate.part))))
            return Optional.of(SettledHit.Rejection.TARGET_CHANGED);
        if (!candidate.supportedPhase || !phaseAllowed(candidate.target.entity())) {
            return Optional.of(SettledHit.Rejection.UNSUPPORTED_PHASE);
        }
        if (!arena.contains(candidate.target.entity().getLocation())) {
            return Optional.of(SettledHit.Rejection.OUTSIDE_ARENA);
        }
        return Optional.empty();
    }

    /**
     * Applies the calibrated public dragon phase policy; ordinary living targets have no dragon-phase restriction.
     * @param target living native parent
     * @return true for non-dragons or HOVER, CIRCLING and SEARCH_FOR_BREATH_ATTACK_TARGET
     */
    public static boolean phaseAllowed(LivingEntity target) {
        return !(target instanceof EnderDragon dragon) || switch (dragon.getPhase()) {
            case HOVER, CIRCLING, SEARCH_FOR_BREATH_ATTACK_TARGET -> true;
            default -> false;
        };
    }

    /**
     * Reads final native cancellation and replacement of the captured primary.
     * @param group provisional native launch references
     * @return true when a retained event rejects the original launch
     */
    private boolean launchVetoed(Group group) {
        return group.bowEvent != null && (group.bowEvent.isCancelled()
                || !group.bowEvent.getProjectile().getUniqueId().equals(group.primary.shot().projectileId()))
                || group.launchEvent != null && group.launchEvent.isCancelled();
    }

    /**
     * A later player lifecycle event can observe completed native launch dispatch before the next service tick. Recognizes actual valid/collided primaries without inventing a PlayerQuit timing guarantee.
     * @param group provisional group
     * @return true when a non-vetoed captured primary has real physical evidence
     */
    private boolean primaryLaunched(Group group) {
        if (group.primary == null || launchVetoed(group)) {
            return false;
        }
        Arrow arrow = entities.get(group.primary.shot().projectileId());
        return group.physicalRetirement || candidates.containsKey(group.primary.shot().projectileId())
                || arrow != null && arrow.isValid();
    }

    /**
     * Cancels future children after session exit while finalizing the launched primary's single charge and retaining valid airborne entities.
     * @param group launched group with ended session
     */
    private void cancelDelayed(Group group) {
        acceptAmmo(group);
        groups.remove(group.id);
        registry.release(group.id);
        for (UUID id : List.copyOf(group.emitted)) {
            Arrow arrow = entities.get(id);
            if (arrow != null && !arrow.isValid()) {
                retire(id, Retirement.FAILED_LAUNCH);
            }
        }
        record("delayed-cancelled", group.primary, "session ended; launched primary/debit retained");
    }

    /**
     * Idempotently removes a provisional group, retires its emitted entities and restores an unaccepted outstanding charge.
     * @param group failed or stale reservation
     */
    private void fail(Group group) {
        if (groups.remove(group.id) == null) {
            return;
        }
        registry.release(group.id);
        for (UUID id : List.copyOf(group.emitted)) {
            retire(id, Retirement.FAILED_LAUNCH);
        }
        if (!group.accepted) {
            restoreDebit(group);
        }
    }

    /**
     * Commits one group charge and applies its captured Quiver saving once; later failure cannot refund it again.
     * @param group captured, not-yet-accepted firing group
     */
    private void acceptAmmo(Group group) {
        if (group.accepted) {
            return;
        }
        group.accepted = true;
        if (group.quiver != null && group.quiver.saved()) {
            restoreDebit(group);
        }
        record("ammo-settled", group.primary, "profile=" + QuiverFlameProfile.REVISION
        + "; level=" + group.quiver.level() + "; sample=" + group.quiver.sample()
        + "; saved=" + group.quiver.saved() + "; debited=" + group.debitOutstanding);
        group.debitOutstanding = false;
        // The accepted charge is final; cancellation cannot refund it again.
    }

    /**
     * Clears the refund token before inventory/drop callbacks. Death uses the event drops; a full live inventory drops overflow at the player.
     * @param group outstanding single-arrow debit
     */
    private void restoreDebit(Group group) {
        if (!group.debitOutstanding) {
            return;
        }
        group.debitOutstanding = false;
        Player player = Bukkit.getPlayer(group.owner);
        if (refundDeath != null && refundDeath.getPlayer().getUniqueId().equals(group.owner) && !refundDeath.getKeepInventory())
            refundDeath.getDrops().add(group.ammo);
        else if (player != null) player.getInventory().addItem(group.ammo).values()
                .forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
    }

    /**
     * Removes registry claim, candidate, continuity demand and native entity together; repeated retirement has no second receiver/accounting effect.
     * @param id native arrow UUID
     * @param reason diagnostic terminal cause
     */
    private void retire(UUID id, Retirement reason) {
        var owned = registry.lookup(id);
        owned.ifPresent(value -> {
            Group group = groups.get(value.shot().shotId());
            if (group != null && (reason == Retirement.MISS || reason == Retirement.HIT)) {
                group.physicalRetirement = true;
            }
        });
        registry.retire(id);
        candidates.remove(id);
        continuity.retire(id);
        Arrow arrow = entities.remove(id);
        if (arrow != null) {
            arrow.remove();
        }
        owned.ifPresent(value -> record("retired", value, reason.name()));
    }

    /**
     * Appends one observation while evicting the oldest entry at the fixed 512-entry bound.
     * @param kind lifecycle operation
     * @param owned non-null captured provenance
     * @param detail diagnostic text
     */
    private void record(String kind, OwnedProjectile owned, String detail) {
        if (traces.size() == 512) {
            traces.removeFirst();
        }
        traces.addLast(new Trace(kind, owned.shot().projectileId(), owned.shot().ownerId(), now(), detail));
    }

    /**
     * Determines whether native Infinity already prevents ordinary-arrow consumption.
     * @param bow native bow, possibly null
     * @param consumable native consumable, possibly null
     * @return true only for ordinary arrows and native Infinity
     */
    private static boolean nativeInfinity(ItemStack bow, ItemStack consumable) {
        return bow != null && consumable != null && consumable.getType() == Material.ARROW
                && bow.containsEnchantment(Enchantment.INFINITY);
    }

    /**
     * Restores the native release charge for a suppressed/vetoed survival event, excluding creative and Infinity consumption.
     * @param player shooter
     * @param consumable native item, possibly null/air
     * @param bow native bow used to detect Infinity
     */
    private void refundNative(Player player, ItemStack consumable, ItemStack bow) {
        if (player.getGameMode() != GameMode.CREATIVE && consumable != null && !consumable.getType().isAir()
                && !nativeInfinity(bow, consumable))
            player.getInventory().addItem(consumable.asQuantity(1)).values().forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
    }

    /**
     * Sends transient firing rejection feedback without changing inventory or chat history.
     * @param player recipient
     * @param message plain diagnostic text
     */
    private void feedback(Player player, String message) {
        player.sendActionBar(Component.text(message));
    }

    /**
     * Resolves a required admission rather than silently creating one.
     * @throws NullPointerException if no admission exists
     * @param id generation UUID
     * @return current admission
     */
    private Arena requireArena(UUID id) {
        return Objects.requireNonNull(arenas.get(id), "Encounter not admitted");
    }

    /**
     * Finds the admitted nonoverlapping arena containing a position.
     * @param location native world and position
     * @return matching arena, or null outside all admissions
     */
    private Arena arena(Location location) {
        return arenas.values().stream().filter(a -> a.contains(location)).findFirst().orElse(null);
    }

    /**
     * Copies mutable Bukkit bounds into immutable domain coordinates.
     * @param bounds Bukkit block-coordinate box
     * @return value box
     */
    private static TracerRules.Box box(BoundingBox bounds) {
        return new TracerRules.Box(vector(bounds.getMin()), vector(bounds.getMax()));
    }

    /**
     * Copies a mutable Bukkit vector across the domain boundary.
     * @param value Bukkit coordinates or velocity
     * @return immutable vector with unchanged components
     */
    private static Vector3 vector(Vector value) {
        return new Vector3(value.getX(), value.getY(), value.getZ());
    }

    /**
     * Widens the current unsigned Bukkit tick without using wall-clock time.
     * @return tick in the unsigned 32-bit Bukkit range
     */
    private static long now() {
        return Integer.toUnsignedLong(Bukkit.getCurrentTick());
    }

    /**
     * Rejects access outside the classic Paper primary thread.
     * @throws IllegalStateException if called off-thread
     */
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Firing requires server thread");
        }
    }

    /**
     * Requires the owning thread and an open service before new work.
     * @throws IllegalStateException if off-thread or closed
     */
    private void check() {
        thread();
        if (closed) {
            throw new IllegalStateException("Firing is closed");
        }
    }

    /**
     * Idempotently fails pending groups, retires arrows, closes continuity/tickets and cancels the sole task before clearing all admission/callback state. Call on the server thread; this does not own encounter HP or native dragon removal.
     */
    @Override public void close() {
        thread();
        if (closed) {
            return;
        }
        for (Group group : List.copyOf(groups.values())) {
            fail(group);
        }
        for (UUID id : List.copyOf(entities.keySet())) {
            retire(id, Retirement.DISABLE);
        }
        continuity.close();
        closed = true;
        if (task != null) {
            task.cancel();
        }
        task = null;
        registry.clear();
        candidates.clear();
        inputs.clear();
        holds.clear();
        sessions.clear();
        sessionArenas.clear();
        cooldowns.clear();
        targets.clear();
        arenas.clear();
        receiver = null;
        retainedProtection = null;
    }
}
