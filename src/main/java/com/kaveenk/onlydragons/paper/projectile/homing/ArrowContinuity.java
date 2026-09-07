package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/** Attached to the existing firing lifetime. Owns diagnostics/tickets, never projectile identity or a task. */
public final class ArrowContinuity implements AutoCloseable {
    public record Frame(long tick, long launchAge, Vector3 position, Vector3 before, Vector3 after,
                        Optional<TracerRules.Aim> aim, int previousLifetime, String profileRevision, long groupLaunchAge) {}
    private final ArenaTickets tickets;
    private boolean closed;
    private final Map<UUID, Frame> frames = new HashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();
    public ArrowContinuity(Plugin plugin) { tickets = new ArenaTickets(plugin); }
    public ArenaTickets tickets() { return tickets; }
    public Optional<Frame> frame(UUID arrow) { thread(); return Optional.ofNullable(frames.get(arrow)); }
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
    public void retire(UUID id) { thread(); frames.remove(id); locks.remove(id); tickets.release(id); }
    public int frameCount() { thread(); return frames.size(); }
    @Override public void close() { thread(); frames.clear(); locks.clear(); tickets.close(); closed = true; }
    private static Vector3 vector(Vector v) { return new Vector3(v.getX(), v.getY(), v.getZ()); }
    private static Vector bukkit(Vector3 v) { return new Vector(v.x(), v.y(), v.z()); }
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Continuity requires server thread"); }
}
