package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile;
import java.util.Objects;
import java.util.UUID;

/** Immutable firing session and tracing provenance, including the original group's grace origin. */
public record OwnedProjectile(ShotContext shot, UUID sessionToken, TracerProfile tracerProfile, long groupLaunchTick) {
    public OwnedProjectile {
        Objects.requireNonNull(shot); Objects.requireNonNull(sessionToken); Objects.requireNonNull(tracerProfile);
        if (groupLaunchTick < 0 || groupLaunchTick > shot.launchTick()) throw new IllegalArgumentException("Invalid group launch tick");
    }
    public OwnedProjectile(ShotContext shot, UUID sessionToken) {
        this(shot, sessionToken, TracerProfile.CALIBRATION_V1, shot.launchTick());
    }
    public OwnedProjectile child(ShotContext child) {
        if (!child.shotId().equals(shot.shotId()) || !child.encounterId().equals(shot.encounterId())
                || !child.parentProjectileId().filter(shot.projectileId()::equals).isPresent())
            throw new IllegalArgumentException("Child must belong to original group");
        return new OwnedProjectile(child, sessionToken, tracerProfile, groupLaunchTick);
    }
}
