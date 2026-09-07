package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;

/**
 * Raw resolved value and separately capped effective value, both in the stat's unit.
 * <p>
 * @param raw finite nonnegative result before the effective cap
 * @param effective finite nonnegative result no greater than raw; e.g. 750/500 Ferocity
 */
public record StatValue(double raw, double effective) {
    /**
     * Rejects non-finite, negative, or cap-increased values without rounding.
     */
    public StatValue {
        DomainChecks.nonNegative(raw, "raw");
        DomainChecks.nonNegative(effective, "effective");
        if (effective > raw) throw new IllegalArgumentException("An effective cap cannot increase a raw value");
    }
}
