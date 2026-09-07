package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import java.util.Objects;
import java.util.Set;

/** Trusted, immutable OnlyDragons calibration. No item-supplied profile field is decoded. */
public enum TracerProfile {
    CALIBRATION_V1("tracer-continuity/v1", 2, 0, 6, 0),
    RETURN_V2("tracer-return/v2", 8, 8, 18, 3),
    AIMED_V3("tracer-aimed/v3", 4, 4, 6, 3);

    private static final Set<String> CURRENT_FIRE_BOWS = Set.of("ordinary_v4", "shortbow_v4", "quiver_v4",
            "flame_v4", "duplex_flame_v4", "tempo_flame_v4");
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
    public boolean requiresLaunchAim() { return this == AIMED_V3; }
    public double aimHalfAngleRadians() { return requiresLaunchAim() ? Math.toRadians(30) : Math.PI; }
    public boolean requiresWholePartBounds() { return this != CALIBRATION_V1; }
    public boolean ballistic(long tick, long groupLaunchTick) {
        if (groupLaunchTick < 0 || tick < groupLaunchTick) throw new IllegalArgumentException("Invalid launch age");
        return tick - groupLaunchTick < grace;
    }
    /** Call only with the registry-resolved definition, never raw PDC identity. */
    public static TracerProfile forDefinition(WeaponDefinition trusted) {
        Objects.requireNonNull(trusted);
        if (ShortbowLoadouts.returningTracer(trusted)
                || (trusted.revision().equals(CalibrationLoadouts.FIRE_REVISION) && CURRENT_FIRE_BOWS.contains(trusted.id()))) return AIMED_V3;
        return (trusted.id().equals("tracer_return_v2") && trusted.revision().equals("tracer-return-v2"))
                ? RETURN_V2 : CALIBRATION_V1;
    }
}
