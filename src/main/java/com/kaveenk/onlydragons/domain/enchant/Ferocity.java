package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;

/** Researched 500 cap; one strict fractional roll, never a recursive child roll. */
public final class Ferocity {
    private Ferocity() {}

    public static double effective(double capturedBase, int bonusPercent) {
        DomainChecks.nonNegative(capturedBase, "capturedBase");
        if (bonusPercent < 0 || bonusPercent > 200) throw new IllegalArgumentException("Tempo bonus outside 0–200");
        return Math.min(500, Math.min(500, capturedBase) * (1 + bonusPercent / 100.0));
    }

    public static int count(double effective, RandomSource random) {
        DomainChecks.nonNegative(effective, "effectiveFerocity");
        double bounded = Math.min(500, effective);
        int whole = (int) (bounded / 100);
        double probability = (bounded % 100) / 100;
        if (probability == 0) return whole;
        double sample = DomainChecks.nonNegative(random.nextDouble(), "random sample");
        if (sample >= 1) throw new IllegalArgumentException("Random sample must be below 1");
        return whole + (sample < probability ? 1 : 0);
    }
}
