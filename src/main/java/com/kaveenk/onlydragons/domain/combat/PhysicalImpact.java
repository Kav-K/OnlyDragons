package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical collision data supplied by the Paper adapter. A record alone is not proof of a hit:
 * the adapter must settle cancellation and resolve a dragon part to the owned parent first.
 * <p>
 * @param key nonnull generation/projectile/target/ordinal identity
 * @param ownerId nonnull captured shooter UUID
 * @param tick nonnegative evaluation tick; {@link com.kaveenk.onlydragons.domain.projectile.SettledHit} retains collision time separately
 * @param position nonnull finite impact position in world blocks
 * @param targetPart nonnull optional nonblank diagnostic part label; never an idempotency component
 */
public record PhysicalImpact(Key key, UUID ownerId, long tick, Vector3 position, Optional<String> targetPart) {
    /**
     * Idempotency identity, distinct even when multiple arrows arrive in the same tick.
     * <p>
     * @param encounterId nonnull generation UUID
     * @param projectileId nonnull real arrow UUID
     * @param targetId nonnull managed parent target UUID
     * @param impactOrdinal nonnegative adapter-supplied impact ordinal, not the encounter commit ordinal
     */
    public record Key(UUID encounterId, UUID projectileId, UUID targetId, int impactOrdinal) {
        /**
         * Rejects null IDs and negative ordinals; collision proof remains the adapter's responsibility.
         */
        public Key {
            Objects.requireNonNull(encounterId, "encounterId");
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(targetId, "targetId");
            if (impactOrdinal < 0) throw new IllegalArgumentException("impactOrdinal must be nonnegative");
        }
    }

    /**
     * Validates required values, nonnegative tick and an optional nonblank part label.
     */
    public PhysicalImpact {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerId, "ownerId");
        DomainChecks.nonNegative(tick, "tick");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(targetPart, "targetPart").ifPresent(part -> DomainChecks.text(part, "targetPart"));
    }
}
