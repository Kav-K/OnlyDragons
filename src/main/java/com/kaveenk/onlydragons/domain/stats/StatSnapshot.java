package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete immutable offense/equipment snapshot. Live target health is stored separately.
 * <p>
 * Safe to retain after equipment changes; no cache invalidation can rewrite this value.
 * @param revision nonblank caller-allocated snapshot identity, not automatically incremented here
 * @param values complete nonnull map for every {@link StatKey}; copied in enum order
 * @param provenance nonnull contributions copied and sorted in canonical order; duplicates retained
 */
public record StatSnapshot(String revision, Map<StatKey, StatValue> values, List<StatModifier> provenance) {
    /**
     * Validates completeness and freezes values/provenance. This constructor does not resolve
     * modifiers or prove that supplied values agree with provenance; use {@link StatResolver} for that.
     */
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

    /**
     * Returns the pre-cap value in the nonnull key's unit; raw crit chance may exceed 100.
     */
    public double raw(StatKey key) { return values.get(Objects.requireNonNull(key, "key")).raw(); }
    /**
     * Returns the separately capped value in the nonnull key's unit; no live equipment is consulted.
     */
    public double effective(StatKey key) { return values.get(Objects.requireNonNull(key, "key")).effective(); }

    /**
     * Ordinary crit probability is separate from raw chance retained for later Overload behavior.
     * <p>
     * Returns a probability in [0,1] from effective chance/100. For raw 175 and effective 25,
     * this returns 0.25 while {@link #raw(StatKey)} still exposes 175.
     */
    public double ordinaryCritProbability() {
        return Math.min(1.0, effective(StatKey.CRIT_CHANCE) / 100.0);
    }
}
