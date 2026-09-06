package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Classic Paper server-thread composition boundary. No combat engine or target HP mutation. */
public final class OwnedBowService implements AutoCloseable {
    public enum Retirement { HIT, MISS, FAILED_LAUNCH, REMOVED, ARENA_EXIT, OWNER_DEATH, RESET, DISABLE }
    public record Trace(String kind, UUID projectileId, UUID ownerId, long tick, String detail) {}
    private record Arena(UUID id, World world, BoundingBox bounds, MechanicRevision mechanic) {
        boolean contains(Location l) { return world.equals(l.getWorld()) && bounds.contains(l.toVector()); }
    }
    private record Target(UUID id, UUID encounter, LivingEntity entity) {}
    private record Input(PlayerInteractEvent event, UUID session, long tick) {}
    private record Hold(UUID session, WeaponIdentity weapon) {}
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
        boolean physicalRetirement;
        Group(UUID id, UUID owner, UUID session, Arena arena, ItemStack ammo) {
            this.id = id; this.owner = owner; this.session = session; this.arena = arena; this.ammo = ammo;
        }
    }
    private static final class Candidate {
        final OwnedProjectile owned;
        final ProjectileHitEvent event;
        final Target target;
        final UUID part;
        final long collisionTick;
        final Vector3 position;
        final boolean supportedPhase;
        boolean nativeVeto;
        Candidate(OwnedProjectile owned, ProjectileHitEvent event, Target target, UUID part, long tick) {
            this.owned = owned; this.event = event; this.target = target; this.part = part;
            collisionTick = tick; position = vector(event.getEntity().getLocation().toVector());
            supportedPhase = phaseAllowed(target.entity());
        }
    }
    private final JavaPlugin plugin;
    private final EquipmentStatsService equipment;
    private final RandomSource random;
    private final ArrowRegistry registry;
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
    private PlayerDeathEvent refundDeath;
    private BukkitTask task;
    private boolean closed;

    public OwnedBowService(JavaPlugin plugin, EquipmentStatsService equipment, int capacity, RandomSource random) {
        this.plugin = Objects.requireNonNull(plugin); this.equipment = Objects.requireNonNull(equipment);
        this.random = Objects.requireNonNull(random); registry = new ArrowRegistry(capacity);
    }
    public void start() {
        check(); if (task != null) throw new IllegalStateException("Already started");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    /** Admission exists independently of any target. Bounds are copied; overlapping arenas reject. */
    public void openEncounter(UUID id, World world, BoundingBox bounds, MechanicRevision mechanic) {
        check(); Objects.requireNonNull(id); Objects.requireNonNull(world); Objects.requireNonNull(mechanic);
        if (arenas.containsKey(id) || bounds.getVolume() <= 0 || arenas.values().stream()
                .anyMatch(a -> a.world().equals(world) && a.bounds().overlaps(bounds)))
            throw new IllegalArgumentException("Duplicate/overlapping/empty arena");
        Arena arena = new Arena(id, world, bounds.clone(), mechanic); arenas.put(id, arena);
        Bukkit.getOnlinePlayers().stream().filter(p -> arena.contains(p.getLocation())).forEach(this::activate);
    }
    public void registerTarget(UUID encounterId, UUID targetId, LivingEntity entity) {
        check(); Arena arena = requireArena(encounterId);
        if (!entity.isValid() || entity.isDead() || !arena.contains(entity.getLocation()) || targets.containsKey(entity.getUniqueId())
                || targets.values().stream().anyMatch(t -> t.id().equals(targetId)))
            throw new IllegalArgumentException("Invalid/duplicate target");
        targets.put(entity.getUniqueId(), new Target(Objects.requireNonNull(targetId), encounterId, entity));
    }
    public void unregisterTarget(UUID entityId) { check(); targets.remove(entityId); }
    /** Exactly one accounting receiver. Delivery follows physical registry/entity retirement; consume the
     * immutable delivered claim and currentSession/isCurrentSession, never require a live projectile lookup. */
    public void receiver(Consumer<SettledHit> receiver) {
        check(); if (this.receiver != null) throw new IllegalStateException("Receiver already registered");
        this.receiver = Objects.requireNonNull(receiver);
    }
    public void clearReceiver(Consumer<SettledHit> receiver) { thread(); if (this.receiver == receiver) this.receiver = null; }
    public Optional<UUID> currentSession(UUID owner) { thread(); return Optional.ofNullable(sessions.get(owner)); }
    public boolean isCurrentSession(UUID owner, UUID token) { thread(); return token.equals(sessions.get(owner)); }
    public List<OwnedProjectile> projectiles() { thread(); return registry.snapshot(); }
    public Optional<OwnedProjectile> projectile(UUID id) { thread(); return registry.lookup(id); }
    /** Live entity access for T07 steering, on the server thread only. Never transfers ownership. */
    public Optional<Arrow> arrow(UUID id) { thread(); return Optional.ofNullable(entities.get(id)); }
    public int capacityUsed() { thread(); return registry.used(); }
    public int reservedCapacity() { thread(); return registry.reserved(); }
    public int pendingGroups() { thread(); return groups.size(); }
    public int pendingClaims() { thread(); return candidates.size(); }
    public int taskCount() { thread(); return task == null ? 0 : 1; }
    public List<Trace> trace() { thread(); return List.copyOf(traces); }
    public void activate(Player player) {
        check(); UUID owner = player.getUniqueId(); Arena current = arena(player.getLocation());
        UUID previous = sessions.get(owner);
        if (previous != null && (current == null || !current.id().equals(sessionArenas.get(owner))))
            clearSession(owner, previous, false);
        if (current != null && !player.isDead()) {
            sessions.computeIfAbsent(owner, ignored -> UUID.randomUUID()); sessionArenas.put(owner, current.id());
        }
    }
    /** A stale token cannot clear the replacement session. Airborne arrows survive quit, but not delayed emission. */
    public void clearSession(UUID owner, UUID token, boolean removeAirborne) {
        check(); if (!sessions.remove(owner, token)) return;
        sessionArenas.remove(owner); cooldowns.remove(owner); inputs.remove(owner); holds.remove(owner);
        for (Group group : List.copyOf(groups.values())) if (group.owner.equals(owner) && group.session.equals(token)) {
            if (!removeAirborne && primaryLaunched(group)) cancelDelayed(group);
            else fail(group);
        }
        if (removeAirborne) for (var arrow : registry.snapshot()) if (arrow.shot().ownerId().equals(owner)) retire(arrow.shot().projectileId(), Retirement.OWNER_DEATH);
    }
    void ownerDied(PlayerDeathEvent event) {
        check(); refundDeath = event;
        try {
            UUID owner = event.getPlayer().getUniqueId();
            currentSession(owner).ifPresent(token -> clearSession(owner, token, true));
            // Arena exit/quit can already have invalidated the session while preserving airborne ownership.
            // Death retires that ownership independently of whether a live token still exists.
            for (Group group : List.copyOf(groups.values())) if (group.owner.equals(owner)) fail(group);
            for (var arrow : registry.snapshot()) if (arrow.shot().ownerId().equals(owner))
                retire(arrow.shot().projectileId(), Retirement.OWNER_DEATH);
        }
        finally { refundDeath = null; }
    }
    void targetDied(LivingEntity entity) {
        check(); Target target = targets.get(entity.getUniqueId());
        if (target != null) endEncounter(target.encounter());
    }
    public void endEncounter(UUID id) {
        check(); arenas.remove(id);
        targets.values().removeIf(t -> t.encounter().equals(id));
        for (Group group : List.copyOf(groups.values())) if (group.arena.id().equals(id)) fail(group);
        for (var arrow : registry.snapshot()) if (arrow.shot().encounterId().equals(id)) retire(arrow.shot().projectileId(), Retirement.RESET);
        for (UUID owner : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(owner);
            if (id.equals(sessionArenas.get(owner))) clearSession(owner, sessions.get(owner), false);
        }
    }
    void drawn(EntityShootBowEvent event) {
        check(); if (!(event.getEntity() instanceof Player player)) return;
        Arena arena = arena(player.getLocation()); if (arena == null) return;
        var inspection = equipment.refresh(player);
        ItemReadResult hand = event.getHand() == EquipmentSlot.HAND ? inspection.fingerprint().mainHand() : inspection.fingerprint().offHand();
        if (!(hand instanceof ItemReadResult.Valid valid)) return;
        // T01b defines only main-hand offense. Shortbow native release must never create a second primary.
        if (event.getHand() != EquipmentSlot.HAND || valid.item().resolvedWeapon().firingMode() == WeaponDefinition.FiringMode.SHORTBOW) {
            event.setCancelled(true); refundNative(player, event.getConsumable()); return;
        }
        if (event.isCancelled()) { refundNative(player, event.getConsumable()); return; }
        if (!player.hasPermission("onlydragons.fire") || !(event.getProjectile() instanceof Arrow arrow)
                || event.getForce() <= 0 || !Objects.equals(event.getBow(), player.getInventory().getItemInMainHand())) {
            event.setCancelled(true); refundNative(player, event.getConsumable()); return;
        }
        Group group = reserve(player, arena, valid.item(), event.getConsumable(), true);
        if (group == null) { event.setCancelled(true); refundNative(player, event.getConsumable()); return; }
        try {
            group.bowEvent = event;
            group.primary = capture(player, arrow, group, valid.item(), inspection, event.getForce());
            own(arrow, group.primary, group);
            group.due = now() + 1;
        } catch (RuntimeException failure) { event.setCancelled(true); fail(group); throw failure; }
    }
    void launched(ProjectileLaunchEvent event) {
        check(); var owned = registry.lookup(event.getEntity().getUniqueId());
        if (owned.isEmpty()) return;
        Group group = groups.get(owned.get().shot().shotId());
        if (group != null && owned.get().shot().ordinal() == 0) group.launchEvent = event;
        suppress((Arrow) event.getEntity());
    }
    void interact(PlayerInteractEvent event) {
        check(); if (event.getHand() != EquipmentSlot.HAND || arena(event.getPlayer().getLocation()) == null) return;
        var inspection = equipment.refresh(event.getPlayer());
        if (!(inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid)
                || valid.item().resolvedWeapon().firingMode() != WeaponDefinition.FiringMode.SHORTBOW) return;
        activate(event.getPlayer());
        // Keep the final event object for next-tick veto. Do not poison its cancellation state ourselves.
        inputs.putIfAbsent(event.getPlayer().getUniqueId(), new Input(event, sessions.get(event.getPlayer().getUniqueId()), now()));
    }
    void stopUsing(UUID owner) { holds.remove(owner); }
    void hit(ProjectileHitEvent event) {
        check(); UUID id = event.getEntity().getUniqueId();
        var owned = registry.claim(id); if (owned.isEmpty()) return;
        Entity hit = event.getHitEntity();
        LivingEntity parent = hit instanceof EnderDragonPart part ? part.getParent() : hit instanceof LivingEntity living ? living : null;
        Target target = parent == null ? null : targets.get(parent.getUniqueId());
        if (target == null || !target.encounter().equals(owned.get().shot().encounterId())) { retire(id, Retirement.MISS); return; }
        candidates.put(id, new Candidate(owned.get(), event, target, hit instanceof EnderDragonPart ? hit.getUniqueId() : null, now()));
        record("claim", owned.get(), "target=" + target.id());
    }
    void damage(EntityDamageByEntityEvent event) {
        check(); if (!(event.getDamager() instanceof Arrow arrow) || registry.lookup(arrow.getUniqueId()).isEmpty()) return;
        Candidate candidate = candidates.get(arrow.getUniqueId());
        if (candidate != null && event.isCancelled()) candidate.nativeVeto = true;
        // All owned native damage is inert, including unrelated targets: no second combat authority.
        event.setDamage(0); event.setCancelled(true);
    }
    /** All native damage to registered managed targets is inert; settlement remains the one authority. */
    void protectTarget(EntityDamageEvent event) {
        check(); Entity entity = event.getEntity();
        UUID id = entity instanceof EnderDragonPart part ? part.getParent().getUniqueId() : entity.getUniqueId();
        if (targets.containsKey(id)) { event.setDamage(0); event.setCancelled(true); }
    }
    private void tick() {
        if (closed) return;
        long tick = now();
        // Membership must settle before any delayed emission, including direct A-to-B movement.
        for (UUID owner : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || player.isDead()) clearSession(owner, sessions.get(owner), player != null);
            else activate(player);
        }
        for (Group group : List.copyOf(groups.values())) if (group.due <= tick) settleGroup(group);
        for (Candidate candidate : List.copyOf(candidates.values())) if (candidate.collisionTick < tick) settle(candidate);
        for (var entry : List.copyOf(inputs.entrySet())) {
            Input input = entry.getValue(); if (input.tick() >= tick) continue;
            inputs.remove(entry.getKey()); Player player = input.event().getPlayer();
            if (isCurrentSession(entry.getKey(), input.session()) && input.event().useItemInHand() != org.bukkit.event.Event.Result.DENY) {
                shortbow(player);
                if (input.event().getAction().isRightClick() && player.isHandRaised()) {
                    var inspection = equipment.refresh(player);
                    if (inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid)
                        holds.put(entry.getKey(), new Hold(input.session(), valid.item().instance().identity()));
                }
            }
        }
        for (var entry : List.copyOf(holds.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey()); Hold hold = entry.getValue();
            if (player == null || !player.isHandRaised() || !isCurrentSession(entry.getKey(), hold.session())) { holds.remove(entry.getKey()); continue; }
            var inspection = equipment.refresh(player);
            if (!(inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid) || !hold.weapon().equals(valid.item().instance().identity())) {
                holds.remove(entry.getKey()); continue;
            }
            shortbow(player);
        }
        for (var owned : registry.snapshot()) {
            UUID id = owned.shot().projectileId(); if (candidates.containsKey(id)) continue;
            Arrow arrow = entities.get(id); Arena arena = arenas.get(owned.shot().encounterId());
            if (arrow == null || !arrow.isValid() || arrow.isDead()) retire(id, Retirement.REMOVED);
            else if (arena == null || !arena.contains(arrow.getLocation())) retire(id, Retirement.ARENA_EXIT);
            else if (arrow.isInBlock() || arrow.isOnGround()) retire(id, Retirement.MISS);
        }
    }
    private void shortbow(Player player) {
        if (!player.isOnline() || player.isDead() || !player.hasPermission("onlydragons.fire")) return;
        Arena arena = arena(player.getLocation()); if (arena == null || now() < cooldowns.getOrDefault(player.getUniqueId(), 0L)) return;
        var inspection = equipment.refresh(player);
        if (!(inspection.fingerprint().mainHand() instanceof ItemReadResult.Valid valid)
                || valid.item().resolvedWeapon().firingMode() != WeaponDefinition.FiringMode.SHORTBOW) return;
        Group group = reserve(player, arena, valid.item(), new ItemStack(Material.ARROW), false); if (group == null) return;
        try {
            Vector velocity = player.getEyeLocation().getDirection().multiply(3);
            Arrow arrow = player.launchProjectile(Arrow.class, velocity, created -> {
                created.setVelocity(velocity);
                group.primary = capture(player, created, group, valid.item(), inspection, 1);
                own(created, group.primary, group);
            });
            if (!arrow.isValid() || group.launchEvent != null && group.launchEvent.isCancelled()) { fail(group); return; }
            cooldowns.put(player.getUniqueId(), now() + FiringRules.cooldown(inspection.stats().snapshot().effective(StatKey.ATTACK_SPEED)));
            group.due = now() + 1;
        } catch (RuntimeException failure) { fail(group); throw failure; }
    }
    private Group reserve(Player player, Arena arena, ItemRegistry.ResolvedItem item, ItemStack consumable, boolean nativeDebit) {
        activate(player); UUID token = sessions.get(player.getUniqueId()); if (token == null) return null;
        int duplex = item.enchantments().stream().filter(e -> e.id().equals("duplex")).mapToInt(WeaponDefinition.Enchantment::level).findFirst().orElse(0);
        UUID id = UUID.randomUUID(); if (!registry.reserve(id, duplex == 0 ? 1 : 2)) { feedback(player, "Arrow capacity reached."); return null; }
        ItemStack ammo = null;
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (consumable == null || consumable.getType() != Material.ARROW) { registry.release(id); return null; }
            if (nativeDebit) ammo = consumable.asQuantity(1);
            else for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack != null && stack.isSimilar(consumable) && stack.getAmount() > 0) {
                    ammo = stack.asQuantity(1); player.getInventory().setItem(slot, stack.asQuantity(stack.getAmount() - 1)); break;
                }
            }
            if (ammo == null) { registry.release(id); feedback(player, "An ordinary arrow is required."); return null; }
        }
        Group group = new Group(id, player.getUniqueId(), token, arena, ammo); group.duplex = duplex;
        groups.put(id, group); return group;
    }
    private OwnedProjectile capture(Player player, Arrow arrow, Group group, ItemRegistry.ResolvedItem item,
                                    EquipmentStatsService.Inspection inspection, double force) {
        ShotContext shot = new ShotContext(group.arena.id(), group.id, arrow.getUniqueId(), 0, Optional.empty(),
                player.getUniqueId(), item.instance().identity(), inspection.stats().snapshot(), item.enchantments(),
                group.arena.mechanic(), now(), vector(arrow.getLocation().toVector()), vector(arrow.getVelocity()),
                new CritResolver().roll(inspection.stats().snapshot(), random), force, 1);
        return new OwnedProjectile(shot, group.session);
    }
    private void own(Arrow arrow, OwnedProjectile owned, Group group) {
        suppress(arrow); arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); arrow.setPersistent(false);
        registry.emit(owned); entities.put(arrow.getUniqueId(), arrow); group.emitted.add(arrow.getUniqueId());
        record("emitted", owned, "parent=" + owned.shot().parentProjectileId().map(UUID::toString).orElse("primary"));
    }
    private static void suppress(Arrow arrow) { arrow.setDamage(0); arrow.setCritical(false); arrow.setFireTicks(0); }
    private void settleGroup(Group group) {
        if (!groups.containsKey(group.id)) return;
        Player owner = Bukkit.getPlayer(group.owner);
        if (owner == null || owner.isDead() || !group.arena.contains(owner.getLocation())
                || arenas.get(group.arena.id()) != group.arena || !isCurrentSession(group.owner, group.session)
                || group.bowEvent != null && (group.bowEvent.isCancelled() || !group.bowEvent.getProjectile().getUniqueId().equals(group.primary.shot().projectileId()))
                || group.launchEvent != null && group.launchEvent.isCancelled()) { fail(group); return; }
        Arrow primary = entities.get(group.primary.shot().projectileId());
        if (!group.physicalRetirement && (primary == null || !primary.isValid())) { fail(group); return; }
        if (primary != null) suppress(primary);
        if (group.duplex > 0) {
            try {
                ShotContext shot = group.primary.shot();
                Location location = new Location(group.arena.world(), shot.launchPosition().x(), shot.launchPosition().y(), shot.launchPosition().z());
                if (!group.arena.contains(location) || !group.arena.world().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) { fail(group); return; }
                Arrow child = group.arena.world().spawn(location, Arrow.class, created -> {
                    created.setShooter(Bukkit.getPlayer(group.owner));
                    created.setVelocity(new Vector(shot.initialVelocity().x(), shot.initialVelocity().y(), shot.initialVelocity().z()));
                    own(created, new OwnedProjectile(FiringRules.child(shot, created.getUniqueId(), now(), group.duplex), group.session), group);
                });
                if (!child.isValid()) { fail(group); return; }
            } catch (RuntimeException failure) { fail(group); throw failure; }
        }
        group.accepted = true; registry.release(group.id); groups.remove(group.id);
        record("accepted-group", group.primary, "children=" + (group.duplex > 0 ? 1 : 0));
    }
    private void settle(Candidate candidate) {
        UUID id = candidate.owned.shot().projectileId(); if (!candidates.containsKey(id)) return;
        Optional<SettledHit.Rejection> rejection = rejection(candidate);
        PhysicalImpact impact = new PhysicalImpact(new PhysicalImpact.Key(candidate.owned.shot().encounterId(), id, candidate.target.id(), 0),
                candidate.owned.shot().ownerId(), now(), candidate.position, Optional.ofNullable(candidate.part).map(UUID::toString));
        SettledHit settled = new SettledHit(candidate.owned, impact, candidate.collisionTick, now(), rejection);
        retire(id, Retirement.HIT); // Terminal before invoking a potentially reentrant/throwing receiver.
        record("settled", candidate.owned, rejection.map(Enum::name).orElse("ACCEPTED"));
        if (receiver != null) receiver.accept(settled);
    }
    private Optional<SettledHit.Rejection> rejection(Candidate c) {
        if (c.event.isCancelled()) return Optional.of(SettledHit.Rejection.PHYSICAL_VETO);
        if (c.nativeVeto) return Optional.of(SettledHit.Rejection.NATIVE_VETO);
        Arena arena = arenas.get(c.owned.shot().encounterId());
        if (arena == null) return Optional.of(SettledHit.Rejection.ENCOUNTER_ENDED);
        if (c.target.entity().isDead() || !c.target.entity().isValid()) return Optional.of(SettledHit.Rejection.TARGET_DEAD);
        if (targets.get(c.target.entity().getUniqueId()) != c.target
                || c.part != null && (!(c.event.getHitEntity() instanceof EnderDragonPart part)
                    || !part.getParent().equals(c.target.entity()) || c.target.entity() instanceof EnderDragon dragon
                    && dragon.getParts().stream().noneMatch(p -> p.getUniqueId().equals(c.part))))
            return Optional.of(SettledHit.Rejection.TARGET_CHANGED);
        if (!c.supportedPhase || !phaseAllowed(c.target.entity())) return Optional.of(SettledHit.Rejection.UNSUPPORTED_PHASE);
        if (!arena.contains(c.target.entity().getLocation())) return Optional.of(SettledHit.Rejection.OUTSIDE_ARENA);
        return Optional.empty();
    }
    public static boolean phaseAllowed(LivingEntity target) {
        return !(target instanceof EnderDragon dragon) || switch (dragon.getPhase()) {
            case HOVER, CIRCLING, SEARCH_FOR_BREATH_ATTACK_TARGET -> true;
            default -> false;
        };
    }
    /** A separate later player lifecycle event can observe completed native launch dispatch before our next tick. */
    private boolean primaryLaunched(Group group) {
        if (group.primary == null || group.bowEvent != null && (group.bowEvent.isCancelled()
                || !group.bowEvent.getProjectile().getUniqueId().equals(group.primary.shot().projectileId()))
                || group.launchEvent != null && group.launchEvent.isCancelled()) return false;
        Arrow arrow = entities.get(group.primary.shot().projectileId());
        return group.physicalRetirement || candidates.containsKey(group.primary.shot().projectileId())
                || arrow != null && arrow.isValid();
    }
    private void cancelDelayed(Group group) {
        groups.remove(group.id); registry.release(group.id);
        for (UUID id : List.copyOf(group.emitted)) {
            Arrow arrow = entities.get(id);
            if (arrow != null && !arrow.isValid()) retire(id, Retirement.FAILED_LAUNCH);
        }
        record("delayed-cancelled", group.primary, "session ended; launched primary/debit retained");
    }
    private void fail(Group group) {
        if (groups.remove(group.id) == null) return;
        registry.release(group.id);
        for (UUID id : List.copyOf(group.emitted)) retire(id, Retirement.FAILED_LAUNCH);
        if (!group.accepted && group.ammo != null) {
            Player player = Bukkit.getPlayer(group.owner);
            if (refundDeath != null && refundDeath.getPlayer().getUniqueId().equals(group.owner) && !refundDeath.getKeepInventory())
                refundDeath.getDrops().add(group.ammo);
            else if (player != null) player.getInventory().addItem(group.ammo).values().forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
        }
    }
    private void retire(UUID id, Retirement reason) {
        var owned = registry.lookup(id);
        owned.ifPresent(value -> {
            Group group = groups.get(value.shot().shotId());
            if (group != null && (reason == Retirement.MISS || reason == Retirement.HIT)) group.physicalRetirement = true;
        });
        registry.retire(id); candidates.remove(id);
        Arrow arrow = entities.remove(id); if (arrow != null) arrow.remove();
        owned.ifPresent(value -> record("retired", value, reason.name()));
    }
    private void record(String kind, OwnedProjectile owned, String detail) {
        if (traces.size() == 512) traces.removeFirst();
        traces.addLast(new Trace(kind, owned.shot().projectileId(), owned.shot().ownerId(), now(), detail));
    }
    private void refundNative(Player player, ItemStack consumable) {
        if (player.getGameMode() != GameMode.CREATIVE && consumable != null && !consumable.getType().isAir())
            player.getInventory().addItem(consumable.asQuantity(1)).values().forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
    }
    private void feedback(Player player, String message) { player.sendActionBar(net.kyori.adventure.text.Component.text(message)); }
    private Arena requireArena(UUID id) { return Objects.requireNonNull(arenas.get(id), "Encounter not admitted"); }
    private Arena arena(Location location) { return arenas.values().stream().filter(a -> a.contains(location)).findFirst().orElse(null); }
    private static Vector3 vector(Vector value) { return new Vector3(value.getX(), value.getY(), value.getZ()); }
    private static long now() { return Integer.toUnsignedLong(Bukkit.getCurrentTick()); }
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Firing requires server thread"); }
    private void check() { thread(); if (closed) throw new IllegalStateException("Firing is closed"); }
    @Override public void close() {
        thread(); if (closed) return;
        for (Group group : List.copyOf(groups.values())) fail(group);
        for (UUID id : List.copyOf(entities.keySet())) retire(id, Retirement.DISABLE);
        closed = true; if (task != null) task.cancel(); task = null;
        registry.clear(); candidates.clear(); inputs.clear(); holds.clear(); sessions.clear(); sessionArenas.clear(); cooldowns.clear(); targets.clear(); arenas.clear(); receiver = null;
    }
}
