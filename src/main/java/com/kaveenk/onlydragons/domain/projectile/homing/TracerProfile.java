package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import java.util.Objects;
import java.util.Set;

/**
 * Trusted, immutable OnlyDragons calibration. No item-supplied profile field is decoded.
 * <p>
 * Captured in {@link com.kaveenk.onlydragons.domain.projectile.OwnedProjectile}; later item edits
 * cannot rewrite an airborne arrow. Profiles choose range/grace/angle rules, not collision authority.
 */
public enum TracerProfile {
    /** Original guidance: two blocks per level, no retention margin or ballistic grace, six-degree turns. */ CALIBRATION_V1("tracer-continuity/v1", 2, 0, 6, 0),
    /** Return calibration: eight blocks per level, eight-block retention margin, three-tick grace, eighteen-degree turns. */ RETURN_V2("tracer-return/v2", 8, 8, 18, 3),
    /** Current aimed calibration: four blocks per level, four-block retention margin, three-tick grace and launch-cone gating. */ AIMED_V3("tracer-aimed/v3", 4, 4, 6, 3);

    /**
     * Exact six current v4 grant IDs selecting aimed guidance, even when Tracer is added later by a book.
     */
    private static final Set<String> CURRENT_FIRE_BOWS = Set.of("ordinary_v4", "shortbow_v4", "quiver_v4",
            "flame_v4", "duplex_flame_v4", "tempo_flame_v4");
    private final String revision;
    private final double perLevel, retention, turn;
    private final int grace;
    TracerProfile(String revision, double perLevel, double retention, double degrees, int grace) {
        this.revision = revision; this.perLevel = perLevel; this.retention = retention;
        this.turn = Math.toRadians(degrees); this.grace = grace;
    }
    /**
     * Returns the fixed steering-policy identity retained in flight diagnostics.
     */
    public String revision() { return revision; }
    /**
     * Returns inclusive current-arrow-to-nearest-part distance in blocks: per-level 2/8/4 for
     * v1/v2/v3. Accepts levels 0–5; zero disables acquisition, other levels reject.
     */
    public double radius(int level) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Tracer level must be 0..5");
        return perLevel * level;
    }
    /**
     * Returns acquisition radius plus the profile hysteresis margin for equipped levels; level
     * zero stays zero. Only the same still-eligible target may use this extended radius.
     */
    public double retentionRadius(int level) { return level == 0 ? radius(level) : radius(level) + retention; }
    /**
     * Returns the common per-tick angular bound in radians (v1/v3 six degrees, v2 eighteen).
     */
    public double turnRadians() { return turn; }
    /**
     * Returns true only for aimed v3; acquisition and retention must both pass captured launch direction.
     */
    public boolean requiresLaunchAim() { return this == AIMED_V3; }
    /**
     * Returns v3's 30-degree half-angle in radians, otherwise π for profiles without launch gating.
     */
    public double aimHalfAngleRadians() { return requiresLaunchAim() ? Math.toRadians(30) : Math.PI; }
    /**
     * Returns true for v2/v3; the adapter must validate whole part boxes against arena bounds,
     * not only their nearest aim point. This predicate does not inspect native geometry.
     */
    public boolean requiresWholePartBounds() { return this != CALIBRATION_V1; }
    /**
     * Returns whether elapsed original-group age is below the grace ticks (0 in v1, 3 in v2/v3).
     * Rejects negative origin or tick before origin. Grace suppresses guidance only, not real collision.
     */
    public boolean ballistic(long tick, long groupLaunchTick) {
        if (groupLaunchTick < 0 || tick < groupLaunchTick) throw new IllegalArgumentException("Invalid launch age");
        return tick - groupLaunchTick < grace;
    }
    /**
     * Call only with the registry-resolved definition, never raw PDC identity.
     * <p>
     * Routes exact held-kit/v4 definitions to AIMED_V3, explicit tracer_return_v2 identity to
     * RETURN_V2, and other trusted definitions to CALIBRATION_V1. It does not read enchant level
     * or verify registry authenticity; the caller must supply registry-resolved content.
     */
    public static TracerProfile forDefinition(WeaponDefinition trusted) {
        Objects.requireNonNull(trusted);
        if (ShortbowLoadouts.returningTracer(trusted)
                || (trusted.revision().equals(CalibrationLoadouts.FIRE_REVISION) && CURRENT_FIRE_BOWS.contains(trusted.id()))) return AIMED_V3;
        return (trusted.id().equals("tracer_return_v2") && trusted.revision().equals("tracer-return-v2"))
                ? RETURN_V2 : CALIBRATION_V1;
    }
}
