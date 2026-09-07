package com.kaveenk.onlydragons.domain.encounter.motion;

import com.kaveenk.onlydragons.domain.projectile.Vector3;

/**
 * dragon-orbit/v1. Measured Paper part envelope plus stopping/rotation margin, in blocks.
 * <p>
 * Pure route sampler; the native backend owns center translation, actual part bounds and failure cleanup.
 * @param radius finite horizontal orbit radius in blocks, inclusive 4–8
 */
public record DragonOrbit(double radius) {
    /**
     * Fixed bounded-motion calibration identity, separate from Tracer and combat policy versions.
     */
    public static final String REVISION = "dragon-orbit/v1";
    /**
     * ENVELOPE reserves 12 blocks for measured parts/stopping; MAX_STEP is the 0.25-block
     * per-server-tick backend guard. Sampling itself does not enforce consecutive-step limits.
     */
    public static final double ENVELOPE = 12, MAX_STEP = .25;
    /**
     * 160-game-tick smooth radial-entry duration; elapsed wall time may increase under server lag.
     */
    public static final int ENTRY_TICKS = 160;
    /**
     * Requested pose relative to the configured route center; no entity is moved by this value.
     * @param offset finite block displacement, including the ramped ±1 vertical bob
     * @param yaw orientation in degrees derived from the route phase
     */
    public record Pose(Vector3 offset, float yaw) {}
    /**
     * Rejects non-finite or out-of-range radius before route use.
     */
    public DragonOrbit {
        if (!Double.isFinite(radius) || radius < 4 || radius > 8) throw new IllegalArgumentException("Unsafe orbit radius");
    }
    /**
     * Accepts finite arena radius 16–48 blocks and returns min(8,arenaRadius−12). The smallest
     * arena gets radius 4; native geometry checks remain necessary in the backend.
     */
    public static DragonOrbit forArena(double arenaRadius) {
        if (!Double.isFinite(arenaRadius) || arenaRadius < 16 || arenaRadius > 48)
            throw new IllegalArgumentException("Orbit requires arena radius 16–48");
        return new DragonOrbit(Math.min(8, arenaRadius - ENVELOPE));
    }
    /**
     * Samples nonnegative elapsed game ticks using a smoothstep entry and nominal horizontal
     * speed 0.20 blocks/tick after entry. Returns center-relative offset/yaw without advancing
     * state; callers must supply monotonic elapsed ticks when moving an actual entity.
     */
    public Pose at(long elapsed) {
        if (elapsed < 0) throw new IllegalArgumentException("Negative route age");
        double phase = (elapsed * (.20 / radius)) % (2 * Math.PI);
        double progress = Math.min(1, elapsed / (double) ENTRY_TICKS);
        double ramp = progress * progress * (3 - 2 * progress);
        return new Pose(new Vector3(radius * ramp * Math.sin(phase), ramp * Math.sin(phase),
                radius * ramp * Math.cos(phase)), (float) Math.toDegrees(phase));
    }
}
