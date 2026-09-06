package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stateless resolver: base, flat sum, additive percentage sum, ordered factors, effective cap. */
public final class StatResolver {
    private final StatProfile profile;
    public StatResolver(StatProfile profile) { this.profile = Objects.requireNonNull(profile, "profile"); }

    public ExplainedStatSnapshot resolve(String revision, Map<StatKey, Double> baseOverrides, ModifierSources sources) {
        var overrides = Map.copyOf(baseOverrides);
        var modifiers = sources.modifiers();
        var values = new EnumMap<StatKey, StatValue>(StatKey.class);
        var explanations = new EnumMap<StatKey, ExplainedStatSnapshot.Explanation>(StatKey.class);
        for (StatKey key : StatKey.values()) {
            var definition = profile.definitions().get(key);
            double base = definition.validateRaw(overrides.getOrDefault(key, definition.defaultValue()));
            var steps = new ArrayList<ExplainedStatSnapshot.Step>();
            var flat = layer(modifiers, key, ModifierOperation.FLAT);
            double flatAmount = sum(flat);
            double result = finite(base + flatAmount);
            steps.add(new ExplainedStatSnapshot.Step("flat", flatAmount, result, flat));
            var percent = layer(modifiers, key, ModifierOperation.ADDITIVE_PERCENT);
            double percentAmount = sum(percent);
            double factor = finite(1 + percentAmount / 100);
            // A negative layer is invalid even if a later negative percentage would reverse its sign.
            DomainChecks.nonNegative(result, "post-flat " + key.id());
            DomainChecks.nonNegative(factor, "additive factor " + key.id());
            result = finite(result * factor);
            steps.add(new ExplainedStatSnapshot.Step("additive_percent", percentAmount, result, percent));
            for (StatModifier modifier : layer(modifiers, key, ModifierOperation.MULTIPLIER)) {
                result = finite(result * modifier.amount());
                steps.add(new ExplainedStatSnapshot.Step("multiplier", modifier.amount(), result, List.of(modifier)));
            }
            double raw = definition.validateRaw(result);
            double cap = profile.effectiveCaps().get(key);
            double effective = Math.min(raw, cap);
            steps.add(new ExplainedStatSnapshot.Step("cap", cap, effective, List.of()));
            values.put(key, new StatValue(raw, effective));
            explanations.put(key, new ExplainedStatSnapshot.Explanation(base, steps));
        }
        return new ExplainedStatSnapshot(new StatSnapshot(revision, values, modifiers), profile.revision(), explanations);
    }

    private static List<StatModifier> layer(List<StatModifier> modifiers, StatKey key, ModifierOperation operation) {
        return modifiers.stream().filter(modifier -> modifier.key() == key && modifier.operation() == operation).toList();
    }
    private static double sum(List<StatModifier> modifiers) {
        double result = 0;
        for (StatModifier modifier : modifiers) result = finite(result + modifier.amount());
        return result;
    }
    private static double finite(double value) { return DomainChecks.finite(value, "stat arithmetic"); }
}
