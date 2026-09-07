package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import java.util.Objects;
import java.util.Optional;

/**
 * One terminal delivery after physical registry retirement, including rejection. Consumers use this
 * immutable value and current session lookup; accounting uses settlementTick, never collisionTick.
 * <p>
 * @param projectile nonnull immutable captured ownership/session/profile
 * @param impact nonnull candidate with settlement tick and matching projectile/owner/generation
 * @param collisionTick nonnegative original native observation tick
 * @param settlementTick tick at or after collision; must equal impact.tick()
 * @param rejection nonnull optional adapter rejection; no reason means adapter-admitted, not yet credited
 */
public record SettledHit(OwnedProjectile projectile, PhysicalImpact impact, long collisionTick,
                         long settlementTick, Optional<Rejection> rejection) {
    /**
     * Settled physical/native veto and generation/target/phase/arena failures; accounting translates
     * these adapter reasons into zero-credit domain outcomes.
     */
    public enum Rejection {
        /** The physical-hit event vetoed this claim. */ PHYSICAL_VETO,
        /** The native damage event vetoed this claim. */ NATIVE_VETO,
        /** The captured encounter generation has ended. */ ENCOUNTER_ENDED,
        /** Domain health already reached zero before admission. */ TARGET_DEAD,
        /** The target identity changed before settlement. */ TARGET_CHANGED,
        /** The native target phase cannot accept managed settlement. */ UNSUPPORTED_PHASE,
        /** The adapter rejected the candidate’s arena bounds. */ OUTSIDE_ARENA
    }
    /**
     * Rejects inconsistent clocks and captured identity; it does not prove external event settlement
     * or independently check target liveness.
     */
    public SettledHit {
        Objects.requireNonNull(projectile); Objects.requireNonNull(impact); Objects.requireNonNull(rejection);
        if (collisionTick < 0 || settlementTick < collisionTick || impact.tick() != settlementTick)
            throw new IllegalArgumentException("Invalid settlement clock");
        if (!projectile.shot().projectileId().equals(impact.key().projectileId())
                || !projectile.shot().ownerId().equals(impact.ownerId())
                || !projectile.shot().encounterId().equals(impact.key().encounterId()))
            throw new IllegalArgumentException("Claim ownership mismatch");
    }
    /**
     * Reviewed T04 calibration; physical UUID geometry is not a semantic head selector.
     * <p>
     * Returns uniform 1.0 for all managed parts, including rejected DTOs. This is calibration
     * policy, not a semantic head-damage multiplier.
     */
    public double partScale() { return 1.0; }
    /**
     * Returns whether the adapter supplied no rejection; combat may still reject a later dead/stale target.
     */
    public boolean accepted() { return rejection.isEmpty(); }
}
