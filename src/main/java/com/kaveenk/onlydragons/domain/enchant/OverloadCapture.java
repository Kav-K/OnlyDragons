package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import java.util.OptionalDouble;

/**
 * Immutable launch decision. No impact/proc consumer may draw again. OnlyDragons calibration.
 * <p>
 * Shared unchanged by Duplex; damage applies {@link #multiplier()} after ordinary crit.
 * @param revision exactly {@link #REVISION}
 * @param level 0 absent or equipped I–V
 * @param rawCritChance finite nonnegative raw percentage points, including excess over 100
 * @param sample nonnull optional [0,1) draw, present iff equipped
 * @param megaCritical must equal the strict sampled probability decision
 */
public record OverloadCapture(String revision, int level, double rawCritChance,
                              OptionalDouble sample, boolean megaCritical) {
    /**
     * Supported OnlyDragons expanded-enchant calibration identity; other labels reject.
     */
    public static final String REVISION = "enchant-checkpoint2-v2";

    /**
     * Checks identity, level, sample presence/range and outcome consistency without consuming randomness.
     * @param revision exactly {@link #REVISION}
     * @param level 0 absent or equipped I\u2013V
     * @param rawCritChance finite nonnegative raw percentage points, including excess over 100
     * @param sample nonnull optional [0,1) draw, present iff equipped
     * @param megaCritical must equal the strict sampled probability decision
     */
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

    /**
     * Returns level zero, raw chance zero, no sample and no mega outcome; consumes no random value.
     * @return level-zero capture with no random sample and no mega-critical outcome
     */
    public static OverloadCapture absent() { return new OverloadCapture(REVISION, 0, 0, OptionalDouble.empty(), false); }

    /**
     * Called after the ordinary crit draw, even when equipped probability is exactly zero or one.
     * @param level 0–5; zero returns absent without consulting stats or random
     * @param stats captured stats whose raw CC already includes the trusted item contribution
     * @param random launch-time source, consumed once when equipped
     * @return validated frozen decision
     * @throws IllegalArgumentException for unsupported level or invalid sample
     */
    public static OverloadCapture roll(int level, StatSnapshot stats, RandomSource random) {
        if (level < 0 || level > 5) throw new IllegalArgumentException("Overload level must be 0–5");
        if (level == 0) return absent();
        double raw = stats.raw(StatKey.CRIT_CHANCE);
        double draw = random.nextDouble();
        validateSample(draw);
        return new OverloadCapture(REVISION, level, raw, OptionalDouble.of(draw), draw < probability(raw));
    }

    /**
     * Returns clamp((rawCC−100)/100,0,1); raw 125 gives 0.25 independently of ordinary crit.
     * @param rawCritChance finite nonnegative percentage points
     * @return mega-critical probability in [0,1]
     */
    public static double probability(double rawCritChance) {
        DomainChecks.nonNegative(rawCritChance, "rawCritChance");
        return Math.clamp((rawCritChance - 100) / 100, 0, 1);
    }

    /**
     * Returns 1 for a failed/absent mega roll, otherwise 1+0.1×level (1.1–1.5); no reroll occurs.
     * @return 1 for absent/failed mega-critical, otherwise 1 plus 0.1 times the captured level
     */
    public double multiplier() { return megaCritical ? 1 + level * .1 : 1; }

    /**
     * Enforces finite [0,1) samples, retaining strict equality rejection at probability boundaries.
     */
    private static void validateSample(double sample) {
        if (!Double.isFinite(sample) || sample < 0 || sample >= 1)
            throw new IllegalArgumentException("Random sample must be in [0,1)");
    }
}
