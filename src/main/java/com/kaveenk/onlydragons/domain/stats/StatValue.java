package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;

/** Raw resolved value and separately capped effective value, both in the stat's unit. */
public record StatValue(double raw, double effective) {
    public StatValue {
        DomainChecks.nonNegative(raw, "raw");
        DomainChecks.nonNegative(effective, "effective");
        if (effective > raw) throw new IllegalArgumentException("An effective cap cannot increase a raw value");
    }
}
