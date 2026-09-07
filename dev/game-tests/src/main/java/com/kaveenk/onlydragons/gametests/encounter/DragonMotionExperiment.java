package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.util.Vector;

/**
 * Bounded public-API feasibility experiment using a fixture-owned native dragon.
 * It observes HOVER velocity and applies explicit public teleports while measuring
 * actual multipart positions. This is not the production flight controller, physical
 * collision acceptance or proof of visual smoothness. Context owns the dragon,
 * entity-ticking chunk leases and all server-thread sample callbacks.
 */
public final class DragonMotionExperiment implements Scenario {
    private ScenarioContext context;
    private EnderDragon dragon;
    private Location origin, previous;
    private int tick;
    private double maxStep, maxPartOffset, maxPartStep;
    private Map<UUID, Vector> previousParts = new HashMap<>();
    /**
     * Owns the experimental chunk region before waiting for real entity ticking.
     * @param context isolated fixture scheduling, entities, observations and cleanup
     */
    @Override public void start(ScenarioContext context) {
        this.context = context;
        context.mechanicRevision("dragon-motion-experiment-v1");
        var world = Bukkit.getWorlds().getFirst();
        origin = new Location(world, 160, 100, 160);
        for (int x = 8; x <= 12; x++) for (int z = 8; z <= 12; z++) context.tickChunk(world.getChunkAt(x, z));
        awaitChunks(0);
    }
    /** Waits at most 200 ticks for entity ticking, spawns HOVER, then separates velocity displacement from the teleport experiment. */
    private void awaitChunks(int waited) {
        if (origin.getChunk().getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) {
            if (waited >= 200) throw new IllegalStateException("Chunk never ticks");
            context.later(1, () -> awaitChunks(waited + 1)); return;
        }
        dragon = context.own(origin.getWorld().spawn(origin, EnderDragon.class, d -> {
            d.setPersistent(false); d.setAI(true); d.setGravity(false); d.setPhase(EnderDragon.Phase.HOVER);
        }));
        context.later(10, () -> {
            previous = dragon.getLocation();
            dragon.setVelocity(new Vector(.2, 0, 0));
            context.later(10, () -> {
                context.observe("hover_velocity_displacement", dragon.getLocation().distance(previous));
                dragon.setVelocity(new Vector()); previous = dragon.getLocation();
                move();
            });
        });
    }
    /** Samples parent and real-part motion over 500 public moves, enforcing the radius-16 box on every sample before final bounds. */
    private void move() {
        var actual = dragon.getLocation();
        if (tick > 0) maxStep = Math.max(maxStep, actual.distance(previous));
        for (var part : dragon.getParts()) {
            Vector point = part.getLocation().toVector();
            Vector old = previousParts.put(part.getUniqueId(), point);
            if (old != null) maxPartStep = Math.max(maxPartStep, point.distance(old));
            var box = part.getBoundingBox();
            maxPartOffset = Math.max(maxPartOffset, Math.max(box.getMin().distance(actual.toVector()), box.getMax().distance(actual.toVector())));
            if (box.getMinX() < 144 || box.getMaxX() > 176 || box.getMinY() < 84 || box.getMaxY() > 116 || box.getMinZ() < 144 || box.getMaxZ() > 176)
                throw new IllegalStateException("Actual part escaped radius16 experiment: " + box);
        }
        if (tick == 500) {
            context.observe("maximum_parent_step", maxStep);
            context.observe("maximum_part_step", maxPartStep);
            context.observe("maximum_part_corner_offset", maxPartOffset);
            context.check("public_moves_succeeded", true, true);
            context.check("parent_step_bounded", true, maxStep <= .25);
            context.check("real_parts_follow", true, maxPartStep > .01 && maxPartStep < 1);
            context.check("admitted_hover_retained", EnderDragon.Phase.HOVER.name(), dragon.getPhase().name());
            context.check("actual_parts_within_small_arena", true, true);
            context.finish(); return;
        }
        tick++;
        double radius = 6 * Math.min(1, tick / 120.0);
        double angle = tick * .025;
        Location next = origin.clone().add(radius * Math.sin(angle), Math.sin(angle) * Math.min(1, tick / 120.0), radius * Math.cos(angle));
        next.setYaw((float) Math.toDegrees(angle));
        previous = actual;
        if (!dragon.teleport(next)) throw new IllegalStateException("Public dragon teleport rejected");
        context.later(1, this::move);
    }
}
