package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;

/** Immutable encounter policy. Coefficients are calibration choices, not empirical game constants. */
public record CombatProfile(MechanicRevision mechanic, Mitigation mitigation, Cap cap,
                            double ferocityHealthFraction) {
    public enum Mitigation { NONE, NONNEGATIVE_DEFENSE }
    public enum Cap { NONE, HISTORICAL_2021 }

    public CombatProfile {
        Objects.requireNonNull(mechanic, "mechanic");
        Objects.requireNonNull(mitigation, "mitigation");
        Objects.requireNonNull(cap, "cap");
        DomainChecks.nonNegative(ferocityHealthFraction, "ferocityHealthFraction");
        if (ferocityHealthFraction > 1) throw new IllegalArgumentException("Ferocity HP fraction must be in [0, 1]");
    }

    public static CombatProfile calibration() {
        return new CombatProfile(new MechanicRevision("combat-calibration", "v1"),
                Mitigation.NONNEGATIVE_DEFENSE, Cap.NONE, 1);
    }

    /** Callers supply a revision identifying their chosen experimental coefficient. */
    public static CombatProfile dragonExperiment(MechanicRevision mechanic, double ferocityHealthFraction) {
        return new CombatProfile(mechanic, Mitigation.NONNEGATIVE_DEFENSE, Cap.HISTORICAL_2021, ferocityHealthFraction);
    }

    public double mitigate(double offense, double defense) {
        DomainChecks.nonNegative(offense, "offense");
        DomainChecks.nonNegative(defense, "defense");
        return mitigation == Mitigation.NONE ? offense : offense * (100 / (100 + defense));
    }

    /** Historical output bands; normalize first to avoid overflow at very large maximum HP. */
    public double cap(double damage, double maxHealth) {
        DomainChecks.nonNegative(damage, "damage");
        DomainChecks.nonNegative(maxHealth, "maxHealth");
        if (maxHealth == 0) throw new IllegalArgumentException("maxHealth must be positive");
        if (cap == Cap.NONE) return damage;
        double ratio = damage / maxHealth;
        double output;
        if (ratio <= 0.004) return damage;
        else if (ratio <= 0.024) output = 0.004 + (ratio - 0.004) * 0.1;
        else if (ratio <= 0.224) output = 0.006 + (ratio - 0.024) * 0.01;
        else if (ratio < 2.224) output = 0.008 + (ratio - 0.224) * 0.001;
        else output = 0.01;
        return Math.min(0.01, output) * maxHealth;
    }
}
