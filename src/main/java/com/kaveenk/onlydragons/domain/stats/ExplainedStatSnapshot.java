package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;

/** Arithmetic explanation captured alongside the stable T00 snapshot, without live service references. */
public record ExplainedStatSnapshot(StatSnapshot snapshot, String profileRevision,
                                    Map<StatKey, Explanation> explanations) {
    public record Step(String stage, double operand, double result, List<StatModifier> contributions) {
        public Step {
            DomainChecks.text(stage, "stage");
            DomainChecks.finite(operand, "operand");
            DomainChecks.finite(result, "result");
            contributions = List.copyOf(contributions);
        }
    }
    public record Explanation(double base, List<Step> steps) {
        public Explanation {
            DomainChecks.nonNegative(base, "base");
            steps = List.copyOf(steps);
        }
    }
    public ExplainedStatSnapshot {
        Objects.requireNonNull(snapshot, "snapshot");
        DomainChecks.text(profileRevision, "profileRevision");
        var copy = new EnumMap<StatKey, Explanation>(StatKey.class);
        copy.putAll(explanations);
        for (StatKey key : StatKey.values()) Objects.requireNonNull(copy.get(key), "Missing explanation " + key);
        explanations = Map.copyOf(copy);
    }
}
