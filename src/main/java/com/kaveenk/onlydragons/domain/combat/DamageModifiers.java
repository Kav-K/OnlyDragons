package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Named fractions (0.4 means +40%) and separate factors, applied in source-ID order.
 * <p>
 * These attack fractions differ from {@link com.kaveenk.onlydragons.domain.stats.StatModifier}
 * percentage points. Both maps are copied into case-sensitive sorted immutable maps.
 * @param additiveFractions nonblank source IDs to finite nonnegative fractions; 0.4 means +40%
 * @param multipliers nonblank source IDs to finite nonnegative factors; zero cancels damage
 */
public record DamageModifiers(Map<String, Double> additiveFractions, Map<String, Double> multipliers) {
    /**
     * Validates and copies both maps; null keys/values or invalid numbers reject before publication.
     */
    public DamageModifiers {
        additiveFractions = copy(additiveFractions, "fraction");
        multipliers = copy(multipliers, "multiplier");
    }

    /**
     * Returns empty additive/multiplicative collections, the identity attack transformation.
     */
    public static DamageModifiers none() { return new DamageModifiers(Map.of(), Map.of()); }

    /**
     * Freezes a source map in deterministic ID order, rejecting blank IDs and invalid finite amounts.
     */
    private static Map<String, Double> copy(Map<String, Double> source, String kind) {
        var result = new TreeMap<String, Double>();
        Objects.requireNonNull(source, kind).forEach((id, value) -> result.put(
                DomainChecks.text(id, "modifier id"), DomainChecks.nonNegative(Objects.requireNonNull(value), kind)));
        return Collections.unmodifiableMap(result);
    }
}
