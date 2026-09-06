package com.kaveenk.onlydragons.domain.encounter.motion;

import com.kaveenk.onlydragons.domain.projectile.Vector3;

/** dragon-orbit/v1. Measured Paper part envelope plus stopping/rotation margin, in blocks. */
public record DragonOrbit(double radius) {
    public static final String REVISION = "dragon-orbit/v1";
    public static final double ENVELOPE = 12, MAX_STEP = .25;
    public static final int ENTRY_TICKS = 160;
    public record Pose(Vector3 offset, float yaw) {}
    public DragonOrbit {
        if (!Double.isFinite(radius) || radius < 4 || radius > 8) throw new IllegalArgumentException("Unsafe orbit radius");
    }
    public static DragonOrbit forArena(double arenaRadius) {
        if (!Double.isFinite(arenaRadius) || arenaRadius < 16 || arenaRadius > 48)
            throw new IllegalArgumentException("Orbit requires arena radius 16–48");
        return new DragonOrbit(Math.min(8, arenaRadius - ENVELOPE));
    }
    public Pose at(long elapsed) {
        if (elapsed < 0) throw new IllegalArgumentException("Negative route age");
        double phase = (elapsed * (.20 / radius)) % (2 * Math.PI);
        double progress = Math.min(1, elapsed / (double) ENTRY_TICKS);
        double ramp = progress * progress * (3 - 2 * progress);
        return new Pose(new Vector3(radius * ramp * Math.sin(phase), ramp * Math.sin(phase),
                radius * ramp * Math.cos(phase)), (float) Math.toDegrees(phase));
    }
}
