package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.encounter.TargetState;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.Map;
import java.util.TreeMap;

/** Pure no-Strength arithmetic. All intermediate overflow fails before any encounter mutation. */
public final class DamageCalculator {
    public record Calculation(double rawOffense, double mitigatedDamage, double cappedDamage,
                              Map<String, Double> breakdown) {
        public Calculation {
            DomainChecks.nonNegative(rawOffense, "rawOffense");
            DomainChecks.nonNegative(mitigatedDamage, "mitigatedDamage");
            DomainChecks.nonNegative(cappedDamage, "cappedDamage");
            breakdown = Map.copyOf(breakdown);
        }
    }

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
        double mitigated = profile.mitigate(damage, target.defense());
        return new Calculation(damage, mitigated, profile.cap(mitigated, target.maxHealth()), explanation);
    }

    /** Frozen pre-cap basis is already mitigated. Preserve the parent's raw offense for tracing. */
    public Calculation proc(DamageResult parent, TargetState target, CombatProfile profile) {
        double basis = parent.amounts().mitigatedDamage();
        return new Calculation(parent.amounts().rawOffense(), basis, profile.cap(basis, target.maxHealth()), parent.modifierBreakdown());
    }

    private static double product(double left, double right) {
        return DomainChecks.nonNegative(left * right, "damage product");
    }
}
