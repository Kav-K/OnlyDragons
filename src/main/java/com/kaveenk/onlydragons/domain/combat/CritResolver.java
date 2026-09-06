package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import java.util.Objects;

/** One shot-time draw even at probabilities zero and one; children consume the captured outcome. */
public final class CritResolver {
    public CritOutcome roll(StatSnapshot stats, RandomSource random) {
        Objects.requireNonNull(stats, "stats");
        double sample = DomainChecks.nonNegative(Objects.requireNonNull(random, "random").nextDouble(), "crit sample");
        if (sample >= 1) throw new IllegalArgumentException("Crit sample must be in [0, 1)");
        return sample < stats.ordinaryCritProbability() ? CritOutcome.CRITICAL : CritOutcome.NORMAL;
    }
}
