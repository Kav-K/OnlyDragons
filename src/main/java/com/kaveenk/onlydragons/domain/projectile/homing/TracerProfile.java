package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import java.util.Objects;

/** Trusted, immutable OnlyDragons calibration. No item-supplied profile field is decoded. */
public enum TracerProfile {
    CALIBRATION_V1("tracer-continuity/v1", 2, 0, 6, 0),
    RETURN_V2("tracer-return/v2", 8, 8, 18, 3);

    private final String revision;
    private final double perLevel, retention, turn;
    private final int grace;
    TracerProfile(String revision, double perLevel, double retention, double degrees, int grace) {
        this.revision = revision; this.perLevel = perLevel; this.retention = retention;
        this.turn = Math.toRadians(degrees); this.grace = grace;
    }
    public String revision() { return revision; }
    public double radius(int level) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Tracer level must be 0..5");
        return perLevel * level;
    }
    public double retentionRadius(int level) { return level == 0 ? radius(level) : radius(level) + retention; }
    public double turnRadians() { return turn; }
    public boolean ballistic(long tick, long groupLaunchTick) {
        if (groupLaunchTick < 0 || tick < groupLaunchTick) throw new IllegalArgumentException("Invalid launch age");
        return tick - groupLaunchTick < grace;
    }
    /** Call only with the registry-resolved definition, never raw PDC identity. */
    public static TracerProfile forDefinition(WeaponDefinition trusted) {
        Objects.requireNonNull(trusted);
        return (trusted.id().equals("tracer_return_v2") && trusted.revision().equals("tracer-return-v2"))
                || ShortbowLoadouts.returningTracer(trusted)
                ? RETURN_V2 : CALIBRATION_V1;
    }
}
