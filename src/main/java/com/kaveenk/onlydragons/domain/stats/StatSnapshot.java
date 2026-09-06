package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete immutable offense/equipment snapshot. Live target health is stored separately. */
public record StatSnapshot(String revision, Map<StatKey, StatValue> values, List<StatModifier> provenance) {
    public StatSnapshot {
        DomainChecks.text(revision, "revision");
        Objects.requireNonNull(values, "values");
        var copy = new EnumMap<StatKey, StatValue>(StatKey.class);
        copy.putAll(values);
        for (StatKey key : StatKey.values()) {
            Objects.requireNonNull(copy.get(key), "Missing stat " + key.id());
        }
        values = Collections.unmodifiableMap(copy);
        provenance = Objects.requireNonNull(provenance, "provenance").stream()
                .map(modifier -> Objects.requireNonNull(modifier, "modifier"))
                .sorted(StatModifier.EXPLANATION_ORDER).toList();
    }

    public double raw(StatKey key) { return values.get(Objects.requireNonNull(key, "key")).raw(); }
    public double effective(StatKey key) { return values.get(Objects.requireNonNull(key, "key")).effective(); }

    /** Ordinary crit probability is separate from raw chance retained for later Overload behavior. */
    public double ordinaryCritProbability() {
        return Math.min(1.0, effective(StatKey.CRIT_CHANCE) / 100.0);
    }
}
