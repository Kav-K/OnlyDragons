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

/**
 * Frozen completion record. Granting rewards or persistence is outside this contract.
 * <p>
 * Production completion is frozen by {@link CombatEncounter}; ranking consumes the retained
 * provenance rather than current gear, names or rounded display amounts.
 * @param completionId nonnull lethal impact identity for production output
 * @param encounterId nonnull completed generation
 * @param variantId nonblank selected type ID
 * @param mechanic nonnull retained combat policy identity
 * @param completedTick nonnegative lethal receiver tick
 * @param participants immutable UUID-sorted copy of full-precision totals
 * @param completedOrdinal terminal accepted ordinal; zero only for legacy unprovenanced data
 * @param selection nonnull optional full catalog content, matching variant and mechanic when present
 */
public record EncounterResult(UUID completionId, UUID encounterId, String variantId,
                              MechanicRevision mechanic, long completedTick,
                              Map<UUID, Contribution> participants, long completedOrdinal,
                              Optional<DragonCatalog.Selection> selection) {
    /**
     * Ordinals start at one and include accepted zero/rounded-away contributions.
     * @param tick nonnegative receiver game tick
     * @param ordinal positive encounter-global accepted commit sequence
     */
    public record CommitStamp(long tick, long ordinal) {
        /**
         * Rejects negative ticks and nonpositive ordinals; ordinal/tick ownership across players is checked by ranking.
         * @param tick nonnegative receiver game tick
         * @param ordinal positive encounter-global accepted commit sequence
         */
        public CommitStamp {
            DomainChecks.nonNegative(tick, "tick");
            if (ordinal < 1) throw new IllegalArgumentException("Ordinal must be positive");
        }
    }
    /**
     * Zero damage can establish participation. Positive represented credit changes only advance
     * the last-increase stamp; overkill credit may exceed actual HP removed.
     * @param actualHealthDamage finite nonnegative cumulative domain HP loss
     * @param contributionDamage finite nonnegative cumulative score, retained at full precision
     * @param eyesPlaced nonnegative placeholder count; combat currently supplies zero
     * @param participated whether at least one successful participation is represented
     * @param firstParticipation nonnull optional first accepted commit, including zero
     * @param lastCreditIncrease nonnull optional latest strict represented-total increase
     */
    public record Contribution(double actualHealthDamage, double contributionDamage, int eyesPlaced, boolean participated,
                               Optional<CommitStamp> firstParticipation, Optional<CommitStamp> lastCreditIncrease) {
        /** Legacy/imported fixture value; production commits always supply provenance.
         * @param health finite nonnegative actual HP removed
         * @param credit finite nonnegative credited damage, possibly greater than HP removed
         * @param eyes nonnegative legacy eyes count; this constructor does not imply reward eligibility
         * @param participated whether at least one successful participation is represented
         */
        public Contribution(double health, double credit, int eyes, boolean participated) {
            this(health, credit, eyes, participated, Optional.empty(), Optional.empty());
        }
        /**
         * Validates totals and internal stamp consistency. Legacy absent stamps remain constructible;
         * {@link RankedEncounterResult} rejects them rather than inventing ranking provenance.
         * @param actualHealthDamage finite nonnegative cumulative domain HP loss
         * @param contributionDamage finite nonnegative cumulative score, retained at full precision
         * @param eyesPlaced nonnegative placeholder count; combat currently supplies zero
         * @param participated whether at least one successful participation is represented
         * @param firstParticipation nonnull optional first accepted commit, including zero
         * @param lastCreditIncrease nonnull optional latest strict represented-total increase
         */
        public Contribution {
            DomainChecks.nonNegative(actualHealthDamage, "actualHealthDamage");
            DomainChecks.nonNegative(contributionDamage, "contributionDamage");
            if (eyesPlaced < 0) throw new IllegalArgumentException("eyesPlaced must be nonnegative");
            Objects.requireNonNull(firstParticipation); Objects.requireNonNull(lastCreditIncrease);
            if (firstParticipation.isPresent() && (!participated
                    || (contributionDamage > 0 && lastCreditIncrease.isEmpty())))
                throw new IllegalArgumentException("Incomplete participation provenance");
            if (lastCreditIncrease.isPresent() && (firstParticipation.isEmpty() || contributionDamage == 0
                    || lastCreditIncrease.get().ordinal() < firstParticipation.get().ordinal()
                    || lastCreditIncrease.get().tick() < firstParticipation.get().tick()))
                throw new IllegalArgumentException("Invalid credit provenance");
        }
    }

    /**
     * Legacy constructor with completed ordinal zero and no full catalog selection. Suitable for
     * unprovenanced DTO fixtures, not production ranking with stamped participants.
     * @param completionId nonnull lethal impact identity for production output
     * @param encounterId nonnull completed generation
     * @param variantId nonblank selected type ID
     * @param mechanic nonnull retained combat policy identity
     * @param completedTick nonnegative lethal receiver tick
     * @param participants immutable UUID-sorted copy of full-precision totals
     */
    public EncounterResult(UUID completionId, UUID encounterId, String variantId, MechanicRevision mechanic,
                           long completedTick, Map<UUID, Contribution> participants) {
        this(completionId, encounterId, variantId, mechanic, completedTick, participants, 0, Optional.empty());
    }

    /**
     * Freezes participants, validates optional selection identity and rejects stamps later than
     * completion tick/ordinal. Cross-participant ordinal conflicts are checked by the ranking projection.
     * @param completionId nonnull lethal impact identity for production output
     * @param encounterId nonnull completed generation
     * @param variantId nonblank selected type ID
     * @param mechanic nonnull retained combat policy identity
     * @param completedTick nonnegative lethal receiver tick
     * @param participants immutable UUID-sorted copy of full-precision totals
     * @param completedOrdinal terminal accepted ordinal; zero only for legacy unprovenanced data
     * @param selection nonnull optional full catalog content, matching variant and mechanic when present
     */
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
        for (var contribution : copy.values()) {
            for (var stamp : java.util.stream.Stream.concat(contribution.firstParticipation().stream(),
                    contribution.lastCreditIncrease().stream()).toList()) {
                if (stamp.tick() > completedTick || stamp.ordinal() > completedOrdinal)
                    throw new IllegalArgumentException("Participant provenance exceeds completion");
            }
        }
        participants = Collections.unmodifiableMap(copy);
    }
}
