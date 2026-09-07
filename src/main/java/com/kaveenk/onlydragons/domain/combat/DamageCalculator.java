package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.encounter.TargetState;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.Map;
import java.util.TreeMap;

/** Pure no-Strength arithmetic. All intermediate overflow fails before any encounter mutation. */
public final class DamageCalculator {
    /**
     * Frozen arithmetic output, before HP policy and remaining-health clipping.
     * @param rawOffense finite nonnegative final offense, including captured crit/mega scaling
     * @param mitigatedDamage finite nonnegative pre-cap damage inherited by Ferocity
     * @param cappedDamage finite nonnegative per-hit contribution basis
     * @param breakdown copied diagnostic map; map iteration order is unspecified
     */
    public record Calculation(double rawOffense, double mitigatedDamage, double cappedDamage,
                              Map<String, Double> breakdown) {
        /**
         * Validates the three amounts and copies diagnostics; it does not prove the arithmetic relations.
         */
        public Calculation {
            DomainChecks.nonNegative(rawOffense, "rawOffense");
            DomainChecks.nonNegative(mitigatedDamage, "mitigatedDamage");
            DomainChecks.nonNegative(cappedDamage, "cappedDamage");
            breakdown = Map.copyOf(breakdown);
        }
    }

    /**
     * Applies weapon × draw × projectile × (1+additive sum) × ordered factors × ordinary crit
     * × captured Overload, then defense and cap. A 100-damage critical with +40% and CD 50
     * produces 210 before mitigation. No random values or live equipment are read.
     * @param shot nonnull immutable captured attack
     * @param modifiers nonnull named attack modifiers, applied once
     * @param target nonnull domain HP/defense snapshot
     * @param profile nonnull encounter policy; profile identity is enforced by CombatEncounter
     * @return immutable damage and explanation; no ledger mutation
     * @throws IllegalArgumentException if an intermediate amount overflows
     */
    public Calculation physical(ShotContext shot, DamageModifiers modifiers, TargetState target, CombatProfile profile) {
        double damage = shot.stats().effective(StatKey.WEAPON_DAMAGE);
        var explanation = new TreeMap<String, Double>();
        explanation.put("weaponDamage", damage);
        explanation.put("drawScale", shot.drawScale());
        explanation.put("projectileScale", shot.projectileScale());
        damage = product(product(damage, shot.drawScale()), shot.projectileScale());
        double additive = 0;
        for (var entry : modifiers.additiveFractions().entrySet()) {
            additive = DomainChecks.nonNegative(additive + entry.getValue(), "additive sum");
            explanation.put("additive/" + entry.getKey(), entry.getValue());
        }
        damage = product(damage, DomainChecks.nonNegative(1 + additive, "additive factor"));
        for (var entry : modifiers.multipliers().entrySet()) {
            damage = product(damage, entry.getValue());
            explanation.put("multiplier/" + entry.getKey(), entry.getValue());
        }
        double crit = shot.crit() == CritOutcome.CRITICAL ? 1 + shot.stats().effective(StatKey.CRIT_DAMAGE) / 100 : 1;
        explanation.put("criticalMultiplier", crit);
        damage = product(damage, crit);
        if (shot.overload().level() > 0) {
            var capture = shot.overload();
            String source = "overload/" + capture.revision() + "/";
            explanation.put(source + "level", (double) capture.level());
            explanation.put(source + "rawCritChance", capture.rawCritChance());
            explanation.put(source + "sample", capture.sample().orElseThrow());
            explanation.put(source + "megaCritical", capture.megaCritical() ? 1d : 0d);
            explanation.put(source + "multiplier", capture.multiplier());
            damage = product(damage, capture.multiplier());
        }
        double mitigated = profile.mitigate(damage, target.defense());
        return new Calculation(damage, mitigated, profile.cap(mitigated, target.maxHealth()), explanation);
    }

    /**
     * Frozen pre-cap basis is already mitigated. Preserve the parent's raw offense for tracing.
     * <p>
     * No crit reroll, bow lookup, modifier reapplication or second mitigation occurs.
     * @param parent accepted physical/Duplex result whose ancestry the encounter has validated
     * @param target current target, used only for maximum HP in the cap
     * @param profile retained encounter policy
     * @return inherited offense and mitigated basis with the cap applied once
     */
    public Calculation proc(DamageResult parent, TargetState target, CombatProfile profile) {
        double basis = parent.amounts().mitigatedDamage();
        return new Calculation(parent.amounts().rawOffense(), basis, profile.cap(basis, target.maxHealth()), parent.modifierBreakdown());
    }

    /**
     * Rejects each non-finite or negative product immediately, including overflow hidden by later factors.
     */
    private static double product(double left, double right) {
        return DomainChecks.nonNegative(left * right, "damage product");
    }
}
