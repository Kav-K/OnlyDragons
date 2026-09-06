package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Named fractions (0.4 means +40%) and separate factors, applied in source-ID order. */
public record DamageModifiers(Map<String, Double> additiveFractions, Map<String, Double> multipliers) {
    public DamageModifiers {
        additiveFractions = copy(additiveFractions, "fraction");
        multipliers = copy(multipliers, "multiplier");
    }

    public static DamageModifiers none() { return new DamageModifiers(Map.of(), Map.of()); }

    private static Map<String, Double> copy(Map<String, Double> source, String kind) {
        var result = new TreeMap<String, Double>();
        Objects.requireNonNull(source, kind).forEach((id, value) -> result.put(
                DomainChecks.text(id, "modifier id"), DomainChecks.nonNegative(Objects.requireNonNull(value), kind)));
        return Collections.unmodifiableMap(result);
    }
}
