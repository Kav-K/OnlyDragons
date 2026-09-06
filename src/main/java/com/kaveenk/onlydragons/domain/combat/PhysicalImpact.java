package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical collision data supplied by the Paper adapter. A record alone is not proof of a hit:
 * the adapter must settle cancellation and resolve a dragon part to the owned parent first.
 */
public record PhysicalImpact(Key key, UUID ownerId, long tick, Vector3 position, Optional<String> targetPart) {
    /** Idempotency identity, distinct even when multiple arrows arrive in the same tick. */
    public record Key(UUID encounterId, UUID projectileId, UUID targetId, int impactOrdinal) {
        public Key {
            Objects.requireNonNull(encounterId, "encounterId");
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(targetId, "targetId");
            if (impactOrdinal < 0) throw new IllegalArgumentException("impactOrdinal must be nonnegative");
        }
    }

    public PhysicalImpact {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerId, "ownerId");
        DomainChecks.nonNegative(tick, "tick");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(targetPart, "targetPart").ifPresent(part -> DomainChecks.text(part, "targetPart"));
    }
}
