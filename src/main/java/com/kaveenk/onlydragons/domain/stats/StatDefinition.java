package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;

/**
 * A configured raw-value range, not an effective gameplay cap.
 * <p>
 * Used by {@link StatProfile} and {@link StatResolver}; units come from {@link StatKey}.
 * @param key nonnull stat identity
 * @param defaultValue finite default inside the inclusive range
 * @param minimumValue finite nonnegative lower bound
 * @param maximumValue finite upper bound at least minimumValue
 */
public record StatDefinition(StatKey key, double defaultValue, double minimumValue, double maximumValue) {
    /**
     * Validates the range and default together; rejects null key or an invalid numeric candidate.
     */
    public StatDefinition {
        Objects.requireNonNull(key, "key");
        DomainChecks.nonNegative(minimumValue, "minimumValue");
        DomainChecks.nonNegative(maximumValue, "maximumValue");
        DomainChecks.nonNegative(defaultValue, "defaultValue");
        if (minimumValue > maximumValue || defaultValue < minimumValue || defaultValue > maximumValue) {
            throw new IllegalArgumentException("Invalid stat range or default for " + key.id());
        }
    }

    /**
     * Checks a base or final raw result before any effective cap.
     * @param value finite candidate in {@link #unit()}
     * @return unchanged candidate
     * @throws IllegalArgumentException for non-finite or out-of-range input
     */
    public double validateRaw(double value) {
        DomainChecks.finite(value, key.id());
        if (value < minimumValue || value > maximumValue) {
            throw new IllegalArgumentException("Out-of-range " + key.id());
        }
        return value;
    }

    /**
     * Returns the fixed unit of the nonnull key; no numeric conversion occurs.
     */
    public StatUnit unit() { return key.unit(); }
}
