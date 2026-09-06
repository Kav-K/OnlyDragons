package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Frozen completion record. Granting rewards or persistence is outside this contract. */
public record EncounterResult(UUID completionId, UUID encounterId, String variantId,
                              MechanicRevision mechanic, long completedTick,
                              Map<UUID, Contribution> participants) {
    public record Contribution(double actualHealthDamage, double contributionDamage, int eyesPlaced, boolean participated) {
        public Contribution {
            DomainChecks.nonNegative(actualHealthDamage, "actualHealthDamage");
            DomainChecks.nonNegative(contributionDamage, "contributionDamage");
            if (eyesPlaced < 0) throw new IllegalArgumentException("eyesPlaced must be nonnegative");
        }
    }

    public EncounterResult {
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(encounterId, "encounterId");
        DomainChecks.text(variantId, "variantId");
        Objects.requireNonNull(mechanic, "mechanic");
        DomainChecks.nonNegative(completedTick, "completedTick");
        var copy = new TreeMap<UUID, Contribution>();
        Objects.requireNonNull(participants, "participants").forEach((id, contribution) ->
                copy.put(Objects.requireNonNull(id, "participant id"), Objects.requireNonNull(contribution, "contribution")));
        participants = Collections.unmodifiableMap(copy);
    }
}
