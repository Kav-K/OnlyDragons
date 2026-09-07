package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.motion.DragonOrbit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Backend-owned route; advanced by the existing combat tick, never a separate scheduler. */
public final class DragonFlight {
    public enum Mode { STATIONARY, ORBIT }
    public record View(String revision, String state, double radius, long steps, double maximumStep) {}
    private final Mode mode;
    private final DragonOrbit orbit;
    private final Location origin;
    private final BoundingBox bounds;
    private long started = -1, lastTick = -1, steps;
    private String state;
    private double maximumStep;
    private Location previous;
    public DragonFlight(Location origin, BoundingBox bounds, Mode mode) {
        this.mode = java.util.Objects.requireNonNull(mode); this.origin = origin.clone(); this.bounds = bounds.clone();
        orbit = DragonOrbit.forArena(Math.min(bounds.getWidthX(), Math.min(bounds.getHeight(), bounds.getWidthZ())) / 2);
        state = mode == Mode.STATIONARY ? "STATIONARY" : "INITIALIZING";
    }
    public View view() { return new View(mode == Mode.STATIONARY ? "dragon-stationary/v1" : DragonOrbit.REVISION, state,
            mode == Mode.STATIONARY ? 0 : orbit.radius(), steps, maximumStep); }
    public double radius() { return mode == Mode.STATIONARY ? 0 : orbit.radius(); }
    public void stop() { if (mode == Mode.ORBIT && !state.startsWith("FAILED")) state = "STOPPED"; }
    public void tick(EnderDragon entity) {
        if (mode == Mode.STATIONARY || state.equals("STOPPED") || state.startsWith("FAILED")) return;
        long tick = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        if (tick == lastTick) return;
        lastTick = tick;
        if (entity.getTicksLived() < 2) return; // Native AI initializes multipart geometry during its first ticks.
        try {
            if (!entity.isValid() || entity.isDead() || !entity.hasAI() || entity.getPhase() != EnderDragon.Phase.HOVER)
                throw new IllegalStateException("Motion lost its live AI-enabled HOVER phase");
            checkBox(entity.getBoundingBox());
            if (entity.getParts().size() != 8) throw new IllegalStateException("Unexpected native part count");
            for (var part : entity.getParts()) checkBox(part.getBoundingBox());
            Location current = entity.getLocation();
            if (!current.getWorld().equals(origin.getWorld())) throw new IllegalStateException("Dragon left its world");
            if (previous != null && current.distance(previous) > 1e-6) throw new IllegalStateException("Dragon moved outside its route controller");
            if (started < 0) started = tick;
            var pose = orbit.at(tick - started);
            var offset = pose.offset();
            Location next = origin.clone().add(offset.x(), offset.y(), offset.z()); next.setYaw(pose.yaw());
            double step = current.distance(next);
            if (step > DragonOrbit.MAX_STEP || yawDistance(current.getYaw(), next.getYaw()) > 3.00001)
                throw new IllegalStateException("Unsafe route step or yaw");
            entity.setVelocity(new Vector());
            if (!entity.teleport(next) || !entity.isValid() || !entity.getWorld().equals(origin.getWorld())
                    || entity.getLocation().distance(next) > 1e-6
                    || yawDistance(entity.getLocation().getYaw(), next.getYaw()) > 1e-4
                    || Math.abs(entity.getLocation().getPitch() - next.getPitch()) > 1e-4)
                throw new IllegalStateException("Public dragon movement rejected or redirected");
            previous = next; maximumStep = Math.max(maximumStep, step); steps++; state = "MOVING";
        } catch (RuntimeException failure) {
            state = "FAILED: " + failure.getMessage();
            throw failure; // Existing combat failure path retires the generation and native entity.
        }
    }
    private void checkBox(BoundingBox box) {
        if (box.getMinX() < bounds.getMinX()+.5 || box.getMaxX() > bounds.getMaxX()-.5
                || box.getMinY() < bounds.getMinY()+.5 || box.getMaxY() > bounds.getMaxY()-.5
                || box.getMinZ() < bounds.getMinZ()+.5 || box.getMaxZ() > bounds.getMaxZ()-.5)
            throw new IllegalStateException("Native parent/part crossed the arena safety margin");
    }
    private static double yawDistance(float a, float b) { return Math.abs(((b - a + 540) % 360) - 180); }
}
