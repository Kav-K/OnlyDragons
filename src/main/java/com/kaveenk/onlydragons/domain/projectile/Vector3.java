package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;

/**
 * Immutable world-space coordinates or velocity; no live Bukkit vector crosses the boundary.
 * <p>
 * Units and world identity are supplied by the enclosing contract; this value does not carry a world UUID.
 * @param x finite x component, in blocks for positions or blocks/tick for velocities
 * @param y finite y component in the same unit
 * @param z finite z component in the same unit
 */
public record Vector3(double x, double y, double z) {
    /**
     * Rejects NaN/infinite components; finite components do not guarantee a finite derived magnitude.
     */
    public Vector3 {
        DomainChecks.finite(x, "x");
        DomainChecks.finite(y, "y");
        DomainChecks.finite(z, "z");
    }
}
