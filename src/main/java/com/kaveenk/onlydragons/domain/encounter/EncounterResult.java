package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Optional;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;

/** Frozen completion record. Granting rewards or persistence is outside this contract. */
public record EncounterResult(UUID completionId, UUID encounterId, String variantId,
                              MechanicRevision mechanic, long completedTick,
                              Map<UUID, Contribution> participants, long completedOrdinal,
                              Optional<DragonCatalog.Selection> selection) {
    /** Ordinals start at one and include accepted zero/rounded-away contributions. */
    public record CommitStamp(long tick, long ordinal) {
        public CommitStamp {
            DomainChecks.nonNegative(tick, "tick");
            if (ordinal < 1) throw new IllegalArgumentException("Ordinal must be positive");
        }
    }
    public record Contribution(double actualHealthDamage, double contributionDamage, int eyesPlaced, boolean participated,
                               Optional<CommitStamp> firstParticipation, Optional<CommitStamp> lastCreditIncrease) {
        /** Legacy/imported fixture value; production commits always supply provenance. */
        public Contribution(double health, double credit, int eyes, boolean participated) {
            this(health, credit, eyes, participated, Optional.empty(), Optional.empty());
        }
        public Contribution {
            DomainChecks.nonNegative(actualHealthDamage, "actualHealthDamage");
            DomainChecks.nonNegative(contributionDamage, "contributionDamage");
            if (eyesPlaced < 0) throw new IllegalArgumentException("eyesPlaced must be nonnegative");
            Objects.requireNonNull(firstParticipation); Objects.requireNonNull(lastCreditIncrease);
            if (lastCreditIncrease.isPresent() && (firstParticipation.isEmpty() || contributionDamage == 0
                    || lastCreditIncrease.get().ordinal() < firstParticipation.get().ordinal()
                    || lastCreditIncrease.get().tick() < firstParticipation.get().tick()))
                throw new IllegalArgumentException("Invalid credit provenance");
        }
    }

    public EncounterResult(UUID completionId, UUID encounterId, String variantId, MechanicRevision mechanic,
                           long completedTick, Map<UUID, Contribution> participants) {
        this(completionId, encounterId, variantId, mechanic, completedTick, participants, 0, Optional.empty());
    }

    public EncounterResult {
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(encounterId, "encounterId");
        DomainChecks.text(variantId, "variantId");
        Objects.requireNonNull(mechanic, "mechanic");
        DomainChecks.nonNegative(completedTick, "completedTick");
        DomainChecks.nonNegative(completedOrdinal, "completedOrdinal");
        Objects.requireNonNull(selection);
        selection.ifPresent(value -> {
            if (!value.identity().id().equals(variantId) || !value.combatProfile().mechanic().equals(mechanic))
                throw new IllegalArgumentException("Selection does not match completion");
        });
        var copy = new TreeMap<UUID, Contribution>();
        Objects.requireNonNull(participants, "participants").forEach((id, contribution) ->
                copy.put(Objects.requireNonNull(id, "participant id"), Objects.requireNonNull(contribution, "contribution")));
        participants = Collections.unmodifiableMap(copy);
    }
}
