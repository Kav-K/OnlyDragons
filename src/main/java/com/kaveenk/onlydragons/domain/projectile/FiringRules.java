package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Optional;
import java.util.UUID;

/** firing-calibration/v1; these are explicit sandbox choices, not production balance. */
public final class FiringRules {
    private FiringRules() {}
    /**
     * Returns max(1,ceil(10/(1+AS/100))) game ticks for finite nonnegative attack-speed points.
     * AS 0/100/400 yields 10/5/2 ticks; no scheduler or mutable cooldown is owned here.
     * @param attackSpeed finite nonnegative attack-speed percentage points
     * @return minimum firing interval in game ticks, rounded up and never below one
     * @throws IllegalArgumentException if attack speed is negative or nonfinite
     */
    public static int cooldown(double attackSpeed) {
        DomainChecks.nonNegative(attackSpeed, "attackSpeed");
        return Math.max(1, (int) Math.ceil(10 / (1 + attackSpeed / 100)));
    }
    /**
     * Returns level×0.04 for Duplex I–V (0.04–0.20); unsupported levels throw IllegalArgumentException.
     * @param level captured Duplex level I–V
     * @return secondary projectile scale from 0.04 through 0.20
     * @throws IllegalArgumentException if the level is outside I–V
     */
    public static double duplexScale(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Duplex level must be 1–5");
        return level * 0.04;
    }
    /**
     * Creates ordinal-one secondary with the primary group/owner/weapon/stats/enchants/crit/Overload
     * and launch transform. Its projectile scale is level×0.04, not primary scale multiplied again.
     * Rejects a parent that is already a child and emission earlier than primary launch+1. Firing
     * admission owns one-child count, capacity, IDs and representable timing; this is pure projection.
     * @param primary nonnull captured primary
     * @param id distinct nonnull new real projectile UUID
     * @param tick nonnegative emission game tick
     * @param level captured Duplex I–V
     * @return immutable secondary capture
     */
    public static ShotContext child(ShotContext primary, UUID id, long tick, int level) {
        if (primary.parentProjectileId().isPresent() || tick < primary.launchTick() + 1)
            throw new IllegalArgumentException("Invalid Duplex parent/timing");
        return new ShotContext(primary.encounterId(), primary.shotId(), id, 1,
                Optional.of(primary.projectileId()), primary.ownerId(), primary.weapon(), primary.stats(),
                primary.enchantments(), primary.mechanic(), tick, primary.launchPosition(), primary.initialVelocity(),
                primary.crit(), primary.drawScale(), duplexScale(level), primary.overload());
    }
}
