package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable firing session and tracing provenance, including the original group's grace origin.
 * @param shot nonnull immutable arrow snapshot
 * @param sessionToken nonnull captured lifecycle token, matched separately from owner UUID
 * @param tracerProfile nonnull trusted immutable steering calibration selected at admission
 * @param groupLaunchTick nonnegative original primary tick, no later than this arrow's launch
 */
public record OwnedProjectile(ShotContext shot, UUID sessionToken, TracerProfile tracerProfile, long groupLaunchTick) {
    /**
     * Validates mandatory captures and grace origin; no live registry or session is consulted.
     * @param shot nonnull immutable arrow snapshot
     * @param sessionToken nonnull captured lifecycle token, matched separately from owner UUID
     * @param tracerProfile nonnull trusted immutable steering calibration selected at admission
     * @param groupLaunchTick nonnegative original primary tick, no later than this arrow's launch
     */
    public OwnedProjectile {
        Objects.requireNonNull(shot); Objects.requireNonNull(sessionToken); Objects.requireNonNull(tracerProfile);
        if (groupLaunchTick < 0 || groupLaunchTick > shot.launchTick()) throw new IllegalArgumentException("Invalid group launch tick");
    }
    /**
     * Legacy capture using CALIBRATION_V1 and the shot's own launch tick as the group grace origin.
     * @param shot nonnull immutable arrow snapshot
     * @param sessionToken nonnull captured lifecycle token, matched separately from owner UUID
     */
    public OwnedProjectile(ShotContext shot, UUID sessionToken) {
        this(shot, sessionToken, TracerProfile.CALIBRATION_V1, shot.launchTick());
    }
    /**
     * Copies session/profile/original grace tick to a child whose group, encounter and parent UUID
     * match this arrow. The caller creates a valid offensive child via {@link FiringRules#child};
     * this method does not independently compare all offensive fields.
     * @param child valid offensive child capture produced from this physical parent
     * @return child ownership carrying the same session, profile and original-group launch tick
     * @throws IllegalArgumentException if group, encounter or parent UUID does not match
     */
    public OwnedProjectile child(ShotContext child) {
        if (!child.shotId().equals(shot.shotId()) || !child.encounterId().equals(shot.encounterId())
                || !child.parentProjectileId().filter(shot.projectileId()::equals).isPresent())
            throw new IllegalArgumentException("Child must belong to original group");
        return new OwnedProjectile(child, sessionToken, tracerProfile, groupLaunchTick);
    }
}
