package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Optional;
import java.util.UUID;

/** firing-calibration/v1; these are explicit sandbox choices, not production balance. */
public final class FiringRules {
    private FiringRules() {}
    public static int cooldown(double attackSpeed) {
        DomainChecks.nonNegative(attackSpeed, "attackSpeed");
        return Math.max(1, (int) Math.ceil(10 / (1 + attackSpeed / 100)));
    }
    public static double duplexScale(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Duplex level must be 1–5");
        return level * 0.04;
    }
    public static ShotContext child(ShotContext primary, UUID id, long tick, int level) {
        if (primary.parentProjectileId().isPresent() || tick < primary.launchTick() + 1)
            throw new IllegalArgumentException("Invalid Duplex parent/timing");
        return new ShotContext(primary.encounterId(), primary.shotId(), id, 1,
                Optional.of(primary.projectileId()), primary.ownerId(), primary.weapon(), primary.stats(),
                primary.enchantments(), primary.mechanic(), tick, primary.launchPosition(), primary.initialVelocity(),
                primary.crit(), primary.drawScale(), duplexScale(level), primary.overload());
    }
}
