package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Continuity attached to the existing {@link OwnedBowService} tick and physical UUID.
 * Owns per-arrow diagnostics, target locks and broker demands, not another projectile
 * registry or task. Steering uses the captured profile and current native velocity:
 * it neither teleports arrows nor restores launch speed, gravity or drag. Native age
 * is reset independently of immutable shot/group ages to avoid despawn during ownership.
 */
public final class ArrowContinuity implements AutoCloseable {
    /**
     * One native pre/post steering observation, retained until that arrow retires.
     * @param tick current service tick
     * @param launchAge ticks since this physical arrow launched
     * @param position native block-coordinate position before steering
     * @param before current native velocity in blocks per tick
     * @param after requested velocity with preserved speed and bounded turn
     * @param aim selected actual part/surface, or empty during ballistic/no-target flight
     * @param previousLifetime native lifetime ticks before reset
     * @param profileRevision captured Tracer policy identity
     * @param groupLaunchAge ticks since original primary launch, shared by descendants
     */
    public record Frame(long tick, long launchAge, Vector3 position, Vector3 before, Vector3 after,
                        Optional<TracerRules.Aim> aim, int previousLifetime, String profileRevision, long groupLaunchAge) {}
    private final ArenaTickets tickets;
    private boolean closed;
    private final Map<UUID, Frame> frames = new HashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();
    /**
     * Creates the shared ticket broker without scheduling a second task.
     * @param plugin native ticket owner
     */
    public ArrowContinuity(Plugin plugin) { tickets = new ArenaTickets(plugin); }
    /**
     * Exposes the single broker for cooperating encounter demands; consumers must release only their own IDs.
     * @return owned broker
     */
    public ArenaTickets tickets() { return tickets; }
    /**
     * Reads the latest immutable observation on the server thread.
     * @param arrow native arrow UUID
     * @return latest frame, or empty before first tick/after retirement
     */
    public Optional<Frame> frame(UUID arrow) { thread(); return Optional.ofNullable(frames.get(arrow)); }
    /**
     * Retains a native 3-by-3 footprint, samples admitted real dragon parts, applies captured-profile LOS/acquisition/turn rules, then resets native age. The original group tick governs ballistic grace; no target means native velocity is left unchanged.
     * @throws IllegalStateException if off-thread or closed
     * @throws IllegalArgumentException if the demand lies outside its reserved footprint
     * @param owned immutable shot, session and captured profile
     * @param arrow matching valid native arrow inside its admitted arena
     * @param targets current registered target projections
     * @param tick current unsigned Bukkit tick
     */
    public void tick(OwnedProjectile owned, Arrow arrow, List<OwnedBowService.AdmittedTarget> targets, long tick) {
        thread(); if (closed) throw new IllegalStateException("Continuity is closed");
        UUID id = arrow.getUniqueId();
        tickets.retain(id, owned.shot().encounterId(), arrow.getLocation().getBlockX() >> 4, arrow.getLocation().getBlockZ() >> 4);
        Vector3 position = vector(arrow.getLocation().toVector()), before = vector(arrow.getVelocity());
        int level = owned.shot().enchantments().stream().filter(e -> e.id().equals("dragon_tracer"))
                .mapToInt(e -> e.level()).findFirst().orElse(0);
        List<TracerRules.Part> parts = new ArrayList<>();
        if (level > 0) for (var target : targets) {
            Entity entity = Bukkit.getEntity(target.entityId());
            if (!(entity instanceof EnderDragon dragon) || !dragon.isValid() || dragon.isDead()
                    || !dragon.getWorld().equals(arrow.getWorld()) || !OwnedBowService.phaseAllowed(dragon)
                    || !target.bounds().contains(vector(dragon.getLocation().toVector()))) continue;
            for (var part : dragon.getParts()) {
                var box = part.getBoundingBox();
                if (owned.tracerProfile().requiresWholePartBounds()
                        && (!target.bounds().contains(vector(box.getMin())) || !target.bounds().contains(vector(box.getMax())))) continue;
                parts.add(new TracerRules.Part(target.targetId(), part.getUniqueId(),
                        new TracerRules.Box(vector(box.getMin()), vector(box.getMax()))));
            }
        }
        var aim = owned.tracerProfile().ballistic(tick, owned.groupLaunchTick()) ? Optional.<TracerRules.Aim>empty()
                : TracerRules.acquire(position, level, parts, candidate -> candidate.distance() == 0
                || arrow.getWorld().rayTraceBlocks(arrow.getLocation(), bukkit(TracerRules.subtract(candidate.point(), position)),
                candidate.distance(), FluidCollisionMode.NEVER, false) == null, owned.tracerProfile(), Optional.ofNullable(locks.get(id)),
                owned.shot().launchPosition(), owned.shot().initialVelocity());
        if (aim.isPresent()) locks.put(id, aim.get().part().targetId()); else locks.remove(id);
        Vector3 after = aim.map(a -> TracerRules.steer(before, TracerRules.subtract(a.point(), position), owned.tracerProfile().turnRadians())).orElse(before);
        if (!after.equals(before)) arrow.setVelocity(bukkit(after));
        int lifetime = arrow.getLifetimeTicks();
        arrow.setLifetimeTicks(0); // Native age only. Launch age, gravity, drag and UUID are never rewritten.
        frames.put(id, new Frame(tick, tick - owned.shot().launchTick(), position, before, after, aim, lifetime, owned.tracerProfile().revision(), tick - owned.groupLaunchTick()));
    }
    /**
     * Drops this arrow's frame, lock and ticket demand without owning native removal.
     * @param id native arrow UUID; absent values are harmless
     */
    public void retire(UUID id) { thread(); frames.remove(id); locks.remove(id); tickets.release(id); }
    /**
     * Counts latest per-arrow observations for lifecycle diagnostics.
     * @return number of retained frames
     */
    public int frameCount() { thread(); return frames.size(); }
    /**
     * Clears observations/locks and closes the shared broker on the server thread. The firing owner separately retires native arrows and cancels its task.
     */
    @Override public void close() { thread(); frames.clear(); locks.clear(); tickets.close(); closed = true; }
    /**
     * Copies native coordinates/velocity into an immutable value.
     * @param v mutable Bukkit vector
     * @return value with unchanged components
     */
    private static Vector3 vector(Vector v) { return new Vector3(v.getX(), v.getY(), v.getZ()); }
    /**
     * Creates a fresh native vector for public API operations.
     * @param v immutable domain vector
     * @return new mutable vector with unchanged components
     */
    private static Vector bukkit(Vector3 v) { return new Vector(v.x(), v.y(), v.z()); }
    /**
     * Enforces classic Paper server-thread ownership.
     * @throws IllegalStateException if off-thread
     */
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Continuity requires server thread"); }
}
