package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import java.util.Objects;
import java.util.Optional;

/** One terminal delivery after physical registry retirement, including rejection. Consumers use this
 * immutable value and current session lookup; accounting uses settlementTick, never collisionTick. */
public record SettledHit(OwnedProjectile projectile, PhysicalImpact impact, long collisionTick,
                         long settlementTick, Optional<Rejection> rejection) {
    public enum Rejection { PHYSICAL_VETO, NATIVE_VETO, ENCOUNTER_ENDED, TARGET_DEAD,
        TARGET_CHANGED, UNSUPPORTED_PHASE, OUTSIDE_ARENA }
    public SettledHit {
        Objects.requireNonNull(projectile); Objects.requireNonNull(impact); Objects.requireNonNull(rejection);
        if (collisionTick < 0 || settlementTick < collisionTick || impact.tick() != settlementTick)
            throw new IllegalArgumentException("Invalid settlement clock");
        if (!projectile.shot().projectileId().equals(impact.key().projectileId())
                || !projectile.shot().ownerId().equals(impact.ownerId())
                || !projectile.shot().encounterId().equals(impact.key().encounterId()))
            throw new IllegalArgumentException("Claim ownership mismatch");
    }
    /** Reviewed T04 calibration; physical UUID geometry is not a semantic head selector. */
    public double partScale() { return 1.0; }
    public boolean accepted() { return rejection.isEmpty(); }
}
