package com.kaveenk.onlydragons.domain.projectile;

import java.util.Objects;
import java.util.UUID;

/** Session identity belongs to firing, independently of equipment revisions. */
public record OwnedProjectile(ShotContext shot, UUID sessionToken) {
    public OwnedProjectile { Objects.requireNonNull(shot); Objects.requireNonNull(sessionToken); }
}
