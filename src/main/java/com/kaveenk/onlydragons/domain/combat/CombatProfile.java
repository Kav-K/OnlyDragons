package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;

/**
 * Immutable encounter policy. Coefficients are calibration choices, not empirical game constants.
 * <p>
 * Retain the full value for an encounter; {@link com.kaveenk.onlydragons.domain.MechanicRevision}
 * labels alone do not prove identical content. No runtime reload is performed here.
 * @param mechanic nonnull caller-maintained policy identity
 * @param mitigation nonnull defense transformation
 * @param cap nonnull per-hit cap policy
 * @param ferocityHealthFraction finite fixed HP fraction in [0,1], still validated for level-based policy
 * @param ferocityHealthPolicy nonnull selector; contribution remains the full capped amount
 */
public record CombatProfile(MechanicRevision mechanic, Mitigation mitigation, Cap cap,
                            double ferocityHealthFraction, FerocityHealthPolicy ferocityHealthPolicy) {
    /**
     * FIXED uses the stored fraction; ACTIVE_TEMPO_LEVEL uses the pre-impact shared Tempo source
     * level captured by the coordinator, not the child's refresh-eligibility level.
     */
    public enum FerocityHealthPolicy {
        /** Uses the encounter profile’s constant proc HP fraction. */ FIXED,
        /** Uses the shared active Tempo source level captured before this physical impact. */ ACTIVE_TEMPO_LEVEL
    }

    /**
     * Compatibility constructor selecting FIXED HP scaling; all canonical validation still applies.
     */
    public CombatProfile(MechanicRevision mechanic, Mitigation mitigation, Cap cap, double fraction) {
        this(mechanic, mitigation, cap, fraction, FerocityHealthPolicy.FIXED);
    }
    /**
     * NONE preserves offense; NONNEGATIVE_DEFENSE multiplies by 100/(100+defense).
     */
    public enum Mitigation {
        /** Leaves this transformation stage unchanged. */ NONE,
        /** Applies 100/(100+defense) to nonnegative finite defense. */ NONNEGATIVE_DEFENSE
    }
    /**
     * NONE preserves mitigated damage; HISTORICAL_2021 consumes progressively attenuated bands
     * and limits output to one percent of maximum domain HP.
     */
    public enum Cap {
        /** Leaves this transformation stage unchanged. */ NONE,
        /** Applies attenuated damage bands and the one-percent maximum-HP ceiling. */ HISTORICAL_2021 }

    /**
     * Rejects null policy selectors and non-finite or out-of-range fractions before publication.
     */
    public CombatProfile {
        Objects.requireNonNull(mechanic, "mechanic");
        Objects.requireNonNull(mitigation, "mitigation");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(ferocityHealthPolicy, "ferocityHealthPolicy");
        DomainChecks.nonNegative(ferocityHealthFraction, "ferocityHealthFraction");
        if (ferocityHealthFraction > 1) throw new IllegalArgumentException("Ferocity HP fraction must be in [0, 1]");
    }

    /**
     * Returns combat-calibration/v1: ordinary defense, no cap, full Ferocity HP and credit.
     */
    public static CombatProfile calibration() {
        return new CombatProfile(new MechanicRevision("combat-calibration", "v1"),
                Mitigation.NONNEGATIVE_DEFENSE, Cap.NONE, 1);
    }

    /**
     * Callers supply a revision identifying their chosen experimental coefficient.
     * <p>
     * Returns ordinary defense plus the historical cap and fixed experimental HP fraction.
     * @param mechanic nonnull explicit experiment identity
     * @param ferocityHealthFraction finite fraction in [0,1]; zero permits score-only procs
     * @return immutable candidate; no registry adoption occurs
     */
    public static CombatProfile dragonExperiment(MechanicRevision mechanic, double ferocityHealthFraction) {
        return new CombatProfile(mechanic, Mitigation.NONNEGATIVE_DEFENSE, Cap.HISTORICAL_2021, ferocityHealthFraction);
    }

    /**
     * Versioned sandbox policy; preserves uncapped physical damage and full proc credit.
     * <p>
     * Returns dragon-tempo/v2, with the shared active Tempo level selecting only proc HP.
     * Physical damage and contribution are uncapped; this is a sandbox balance choice.
     */
    public static CombatProfile tempoDragon() {
        return new CombatProfile(new MechanicRevision("dragon-tempo", "v2"),
                Mitigation.NONNEGATIVE_DEFENSE, Cap.NONE, 1, FerocityHealthPolicy.ACTIVE_TEMPO_LEVEL);
    }

    /**
     * Selects HP scaling without mutating buffs or credit.
     * @param activeSourceLevel 0 for absent Tempo, otherwise 1–5; validated even for FIXED
     * @return fixed fraction, or 1.0/0.9/0.8/0.7/0.6/0.5 at levels 0–5
     * @throws IllegalArgumentException for a level outside 0–5
     */
    public double ferocityHealthFraction(int activeSourceLevel) {
        if (activeSourceLevel < 0 || activeSourceLevel > 5) throw new IllegalArgumentException("Tempo source level must be 0–5");
        return ferocityHealthPolicy == FerocityHealthPolicy.FIXED ? ferocityHealthFraction
                : switch (activeSourceLevel) {
                    case 0 -> 1.0;
                    case 1 -> .9;
                    case 2 -> .8;
                    case 3 -> .7;
                    case 4 -> .6;
                    case 5 -> .5;
                    default -> throw new AssertionError();
                };
    }

    /**
     * Applies the selected defense rule once; defense 100 halves offense under NONNEGATIVE_DEFENSE.
     * @param offense finite nonnegative damage points
     * @param defense finite nonnegative defense points, validated even for NONE
     * @return nonnegative mitigated damage; neither input is mutated
     */
    public double mitigate(double offense, double defense) {
        DomainChecks.nonNegative(offense, "offense");
        DomainChecks.nonNegative(defense, "defense");
        return mitigation == Mitigation.NONE ? offense : offense * (100 / (100 + defense));
    }

    /**
     * Historical output bands; normalize first to avoid overflow at very large maximum HP.
     * <p>
     * For maximum HP 1,000,000, inputs 4,000/24,000/224,000/2,224,000 map to
     * 4,000/6,000/8,000/10,000 under HISTORICAL_2021. Apply per hit, not to a volley sum.
     * @param damage finite nonnegative mitigated damage points
     * @param maxHealth finite strictly positive domain maximum HP, validated even for NONE
     * @return capped damage without remaining-HP clipping
     * @throws IllegalArgumentException for invalid numbers
     */
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
