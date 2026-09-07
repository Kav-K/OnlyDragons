package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.OptionalDouble;

/** Owner-selected OnlyDragons balance, not upstream parity. All durations are game ticks. */
public final class QuiverFlameProfile {
    /**
     * Identity recorded with owned ammunition/fire decisions; changing coefficients requires a new policy identity.
     */
    public static final String REVISION = "quiver-flame/v1";
    /**
     * Game-tick cadence/end windows: burns strike every 20 through tick 60 inclusive;
     * vulnerability lasts 1200 ticks with exclusive expiry, coordinated by OwnedFireCoordinator.
     */
    public static final int FIRE_SPACING = 20, FIRE_DURATION = 60, VULNERABILITY_DURATION = 1200;
    private QuiverFlameProfile() {}

    /**
     * Captured ammunition-saving decision, not proof that native inventory was actually debited.
     * @param level 0 absent or Infinite Quiver I–X
     * @param sample nonnull optional finite [0,1) sample, absent iff level is zero
     * @param saved true exactly when equipped sample &lt; level×0.05
     */
    public record Quiver(int level, OptionalDouble sample, boolean saved) {
        /**
         * Rejects unsupported levels and inconsistent sample/decision pairs; no ammunition mutation occurs.
         */
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
    /**
     * Consumes one draw for equipped I–X and none for level zero. A saved decision only permits
     * restoring an actual reserved/debited arrow; inventory settlement belongs to the firing adapter.
     * @param level 0–10
     * @param random caller-owned launch source, consulted only when equipped
     * @return immutable validated saving decision
     */
    public static Quiver capture(int level, RandomSource random) {
        level(level, 10);
        if (level == 0) return new Quiver(0, OptionalDouble.empty(), false);
        double sample = random.nextDouble();
        return new Quiver(level, OptionalDouble.of(sample), sample < level * 0.05);
    }
    /**
     * Returns Flame level×0.03 for 0–2 (zero absent); rejects other levels. The base is accepted
     * physical credit, so Flame II on 100 credit gives potency 6 before vulnerability/cap.
     */
    public static double fireFraction(int level) { level(level, 2); return level * 0.03; }
    /**
     * Returns 1+0.1×Duplex level for 0–5; zero gives neutral 1. Invalid levels reject.
     */
    public static double vulnerability(int level) { level(level, 5); return 1 + level * 0.1; }
    /**
     * Shared inclusive effect-level check; zero denotes an absent effect.
     */
    private static void level(int level, int max) {
        if (level < 0 || level > max) throw new IllegalArgumentException("Unsupported effect level");
    }
}
