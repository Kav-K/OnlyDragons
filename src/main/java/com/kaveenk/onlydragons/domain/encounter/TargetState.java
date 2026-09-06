package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;
import java.util.UUID;

/** Immutable view of authoritative domain HP, independent of the native entity health scale. */
public record TargetState(UUID encounterId, UUID targetId, double maxHealth, double currentHealth, double defense) {
    public TargetState {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetId, "targetId");
        DomainChecks.nonNegative(maxHealth, "maxHealth");
        if (maxHealth == 0) throw new IllegalArgumentException("maxHealth must be positive");
        DomainChecks.nonNegative(currentHealth, "currentHealth");
        if (currentHealth > maxHealth) throw new IllegalArgumentException("Current HP exceeds maximum HP");
        DomainChecks.nonNegative(defense, "defense");
    }

    public boolean alive() { return currentHealth > 0; }
}
