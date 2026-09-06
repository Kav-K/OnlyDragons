package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import java.util.OptionalDouble;

/** Immutable launch decision. No impact/proc consumer may draw again. OnlyDragons calibration. */
public record OverloadCapture(String revision, int level, double rawCritChance,
                              OptionalDouble sample, boolean megaCritical) {
    public static final String REVISION = "enchant-checkpoint2-v2";

    public OverloadCapture {
        if (!REVISION.equals(revision)) throw new IllegalArgumentException("Unsupported Overload revision");
        if (level < 0 || level > 5) throw new IllegalArgumentException("Overload level must be 0–5");
        DomainChecks.nonNegative(rawCritChance, "rawCritChance");
        java.util.Objects.requireNonNull(sample);
        if (sample.isPresent()) validateSample(sample.getAsDouble());
        if ((level > 0) != sample.isPresent()) throw new IllegalArgumentException("Equipped Overload requires one captured draw");
        if (megaCritical != (sample.isPresent() && sample.getAsDouble() < probability(rawCritChance)))
            throw new IllegalArgumentException("Overload outcome disagrees with captured draw");
    }

    public static OverloadCapture absent() { return new OverloadCapture(REVISION, 0, 0, OptionalDouble.empty(), false); }

    /** Called after the ordinary crit draw, even when equipped probability is exactly zero or one. */
    public static OverloadCapture roll(int level, StatSnapshot stats, RandomSource random) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Overload level must be 0–5");
        if (level == 0) return absent();
        double raw = stats.raw(StatKey.CRIT_CHANCE);
        double draw = random.nextDouble();
        validateSample(draw);
        return new OverloadCapture(REVISION, level, raw, OptionalDouble.of(draw), draw < probability(raw));
    }

    public static double probability(double rawCritChance) {
        DomainChecks.nonNegative(rawCritChance, "rawCritChance");
        return Math.clamp((rawCritChance - 100) / 100, 0, 1);
    }

    public double multiplier() { return megaCritical ? 1 + level * .1 : 1; }

    private static void validateSample(double sample) {
        if (!Double.isFinite(sample) || sample < 0 || sample >= 1)
            throw new IllegalArgumentException("Random sample must be in [0,1)");
    }
}
