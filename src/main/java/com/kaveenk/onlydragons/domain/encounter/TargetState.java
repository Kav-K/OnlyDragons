package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of authoritative domain HP, independent of the native entity health scale.
 * <p>
 * Replaced by {@link CombatEncounter} on commits; native health mirroring belongs to a backend.
 * @param encounterId nonnull generation identity
 * @param targetId nonnull managed target identity
 * @param maxHealth finite strictly positive domain HP
 * @param currentHealth finite HP in [0,maxHealth]
 * @param defense finite nonnegative defense points
 */
public record TargetState(UUID encounterId, UUID targetId, double maxHealth, double currentHealth, double defense) {
    /**
     * Rejects null IDs, invalid HP bounds and negative/non-finite defense; does not read a native entity.
     * @param encounterId nonnull generation identity
     * @param targetId nonnull managed target identity
     * @param maxHealth finite strictly positive domain HP
     * @param currentHealth finite HP in [0,maxHealth]
     * @param defense finite nonnegative defense points
     */
    public TargetState {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetId, "targetId");
        DomainChecks.nonNegative(maxHealth, "maxHealth");
        if (maxHealth == 0) throw new IllegalArgumentException("maxHealth must be positive");
        DomainChecks.nonNegative(currentHealth, "currentHealth");
        if (currentHealth > maxHealth) throw new IllegalArgumentException("Current HP exceeds maximum HP");
        DomainChecks.nonNegative(defense, "defense");
    }

    /**
     * Returns whether domain current HP is strictly positive; native animation/removal is a separate state.
     * @return true only while domain current HP is strictly positive
     */
    public boolean alive() { return currentHealth > 0; }
}
