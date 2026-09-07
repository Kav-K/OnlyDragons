package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.OptionalDouble;

/** Owner-selected OnlyDragons balance, not upstream parity. All durations are game ticks. */
public final class QuiverFlameProfile {
    public static final String REVISION = "quiver-flame/v1";
    public static final int FIRE_SPACING = 20, FIRE_DURATION = 60, VULNERABILITY_DURATION = 1200;
    private QuiverFlameProfile() {}

    public record Quiver(int level, OptionalDouble sample, boolean saved) {
        public Quiver {
            QuiverFlameProfile.level(level, 10);
            if (level == 0 ? sample.isPresent() || saved : sample.isEmpty())
                throw new IllegalArgumentException("Quiver capture mismatch");
            if (sample.isPresent()) {
                double value = DomainChecks.nonNegative(sample.getAsDouble(), "quiver sample");
                if (value >= 1 || saved != (value < level * 0.05))
                    throw new IllegalArgumentException("Quiver decision mismatch");
            }
        }
    }
    public static Quiver capture(int level, RandomSource random) {
        level(level, 10);
        if (level == 0) return new Quiver(0, OptionalDouble.empty(), false);
        double sample = random.nextDouble();
        return new Quiver(level, OptionalDouble.of(sample), sample < level * 0.05);
    }
    public static double fireFraction(int level) { level(level, 2); return level * 0.03; }
    public static double vulnerability(int level) { level(level, 5); return 1 + level * 0.1; }
    private static void level(int level, int max) {
        if (level < 0 || level > max) throw new IllegalArgumentException("Unsupported effect level");
    }
}
