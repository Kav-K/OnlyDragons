package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;

/** Immutable world-space coordinates or velocity; no live Bukkit vector crosses the boundary. */
public record Vector3(double x, double y, double z) {
    public Vector3 {
        DomainChecks.finite(x, "x");
        DomainChecks.finite(y, "y");
        DomainChecks.finite(z, "z");
    }
}
