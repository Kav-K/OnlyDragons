package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;

/** Researched 500 cap; one strict fractional roll, never a recursive child roll. */
public final class Ferocity {
    private Ferocity() {}

    /**
     * Applies live Tempo to the captured effective base, capping before and after multiplication.
     * @param capturedBase finite nonnegative Ferocity points, already including trusted Vicious
     * @param bonusPercent shared live Tempo percentage points in 0–200
     * @return value in 0–500; base 25 with +200% yields 75, while zero remains zero
     */
    public static double effective(double capturedBase, int bonusPercent) {
        DomainChecks.nonNegative(capturedBase, "capturedBase");
        if (bonusPercent < 0 || bonusPercent > 200) throw new IllegalArgumentException("Tempo bonus outside 0–200");
        return Math.min(500, Math.min(500, capturedBase) * (1 + bonusPercent / 100.0));
    }

    /**
     * Resolves whole hundreds plus one strict fractional sample. At 250, returns two guaranteed
     * children and a third only when sample &lt; 0.5. Never call recursively for a child.
     * @param effective finite nonnegative Ferocity; values above 500 are capped
     * @param random caller-owned source consulted only for a nonzero fractional remainder
     * @return child count in 0–5
     * @throws IllegalArgumentException for invalid Ferocity or a consulted sample outside [0,1)
     */
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
