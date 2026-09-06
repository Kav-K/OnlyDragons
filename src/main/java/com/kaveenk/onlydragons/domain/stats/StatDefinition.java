package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;

/** A configured raw-value range, not an effective gameplay cap. */
public record StatDefinition(StatKey key, double defaultValue, double minimumValue, double maximumValue) {
    public StatDefinition {
        Objects.requireNonNull(key, "key");
        DomainChecks.nonNegative(minimumValue, "minimumValue");
        DomainChecks.nonNegative(maximumValue, "maximumValue");
        DomainChecks.nonNegative(defaultValue, "defaultValue");
        if (minimumValue > maximumValue || defaultValue < minimumValue || defaultValue > maximumValue) {
            throw new IllegalArgumentException("Invalid stat range or default for " + key.id());
        }
    }

    public double validateRaw(double value) {
        DomainChecks.finite(value, key.id());
        if (value < minimumValue || value > maximumValue) {
            throw new IllegalArgumentException("Out-of-range " + key.id());
        }
        return value;
    }

    public StatUnit unit() { return key.unit(); }
}
