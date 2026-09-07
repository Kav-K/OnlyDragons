package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Arithmetic explanation captured alongside the stable T00 snapshot, without live service references.
 * @param snapshot nonnull complete numeric snapshot
 * @param profileRevision nonblank label of the resolving policy, separate from equipment revision
 * @param explanations complete copied map for all stats; immutable entries retain no live services
 */
public record ExplainedStatSnapshot(StatSnapshot snapshot, String profileRevision,
                                    Map<StatKey, Explanation> explanations) {
    /**
     * One frozen arithmetic observation; the constructor validates finiteness, not a recomputed equation.
     * @param stage nonblank layer label (flat, additive_percent, multiplier, cap in the resolver)
     * @param operand finite sum, percentage points, factor or cap according to stage
     * @param result finite value after the stage, in the stat's unit
     * @param contributions copied nonnull list of source modifiers; empty for a cap
     */
    public record Step(String stage, double operand, double result, List<StatModifier> contributions) {
        /**
         * Copies contributions and rejects blank stages or non-finite arithmetic observations.
         */
        public Step {
            DomainChecks.text(stage, "stage");
            DomainChecks.finite(operand, "operand");
            DomainChecks.finite(result, "result");
            contributions = List.copyOf(contributions);
        }
    }
    /**
     * Ordered resolution history for one stat.
     * @param base finite nonnegative starting value in that stat's unit
     * @param steps immutable copy in application order, ending with the effective cap for resolver output
     */
    public record Explanation(double base, List<Step> steps) {
        /**
         * Validates the base and copies steps without recalculating the caller-supplied history.
         */
        public Explanation {
            DomainChecks.nonNegative(base, "base");
            steps = List.copyOf(steps);
        }
    }
    /**
     * Rejects missing explanations or snapshot and freezes the full map before publication.
     */
    public ExplainedStatSnapshot {
        Objects.requireNonNull(snapshot, "snapshot");
        DomainChecks.text(profileRevision, "profileRevision");
        var copy = new EnumMap<StatKey, Explanation>(StatKey.class);
        copy.putAll(explanations);
        for (StatKey key : StatKey.values()) Objects.requireNonNull(copy.get(key), "Missing explanation " + key);
        explanations = Map.copyOf(copy);
    }
}
