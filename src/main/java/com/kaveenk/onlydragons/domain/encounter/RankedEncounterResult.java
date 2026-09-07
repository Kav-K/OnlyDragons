package com.kaveenk.onlydragons.domain.encounter;

import java.util.*;

/**
 * Immutable placement projection of one authoritative completion; no damage or reward authority.
 * <p>
 * Orders full-precision credit descending, then last strict increase tick/ordinal ascending.
 * Zero-only participants use first participation; signed zeros compare equally. No UUID/name
 * or rounded-display fallback resolves ties: complete unique commit provenance is required.
 */
public final class RankedEncounterResult {
    /**
     * One immutable row created with contiguous one-based placement by the ranking constructor.
     * The public record itself performs no additional validation.
     * @param place one-based position for generated rows
     * @param playerId participant UUID for generated rows
     * @param contribution exact retained full-precision totals and stamps
     */
    public record Placement(int place, UUID playerId, EncounterResult.Contribution contribution) {}

    private final EncounterResult result;
    private final List<Placement> placements;

    /**
     * Retains the nonnull result and builds an immutable ordered projection. Missing production
     * provenance, empty participants, conflicting ordinal ownership/ticks or backdated stamps
     * throw IllegalArgumentException; the input result remains unchanged.
     * @param result nonnull immutable production completion with complete participant commit provenance
     * @throws NullPointerException if result is null
     * @throws IllegalArgumentException if completion/participant provenance is absent, conflicting or backdated
     */
    public RankedEncounterResult(EncounterResult result) {
        this.result = Objects.requireNonNull(result);
        if (result.completedOrdinal() < 1 || result.participants().isEmpty())
            throw new IllegalArgumentException("Ranking requires production completion provenance");
        // A commit ordinal belongs to exactly one participant and tick. First participation
        // and last increase may describe the same commit, but cannot disagree about it.
        /**
         * Local provenance key used to reject conflicting ownership of one accepted ordinal.
         * @param owner participant claiming the ordinal
         * @param tick claimed game tick for that commit
         */
        record OwnerTick(UUID owner, long tick) {}
        var commits = new TreeMap<Long, OwnerTick>();
        result.participants().forEach((id, contribution) -> {
            if (!contribution.participated() || contribution.firstParticipation().isEmpty()
                    || (contribution.contributionDamage() > 0 && contribution.lastCreditIncrease().isEmpty()))
                throw new IllegalArgumentException("Ranking requires complete participant provenance");
            for (var stamp : java.util.stream.Stream.concat(contribution.firstParticipation().stream(),
                    contribution.lastCreditIncrease().stream()).toList()) {
                var value = new OwnerTick(id, stamp.tick());
                var previous = commits.putIfAbsent(stamp.ordinal(), value);
                if (previous != null && !previous.equals(value))
                    throw new IllegalArgumentException("Conflicting accepted commit provenance");
            }
        });
        long tick = -1;
        for (var commit : commits.values()) {
            if (commit.tick() < tick) throw new IllegalArgumentException("Backdated accepted commit provenance");
            tick = commit.tick();
        }
        var ordered = new ArrayList<>(result.participants().entrySet());
        ordered.sort(Comparator.<Map.Entry<UUID, EncounterResult.Contribution>>comparingDouble(
                        entry -> normalizedCredit(entry.getValue())).reversed()
                .thenComparingLong(entry -> stamp(entry.getValue()).tick())
                .thenComparingLong(entry -> stamp(entry.getValue()).ordinal()));
        var ranked = new ArrayList<Placement>();
        for (var entry : ordered) ranked.add(new Placement(ranked.size() + 1, entry.getKey(), entry.getValue()));
        placements = List.copyOf(ranked);
    }

    /**
     * Maps both signed zeros to positive zero before ordering; all positive precision is retained.
     */
    private static double normalizedCredit(EncounterResult.Contribution contribution) {
        return contribution.contributionDamage() == 0 ? 0 : contribution.contributionDamage();
    }

    /**
     * Selects last strict increase for positive credit, first participation for zero-only totals.
     * The constructor validates presence before sorting, so no missing-provenance fallback is needed.
     */
    private static EncounterResult.CommitStamp stamp(EncounterResult.Contribution contribution) {
        return (contribution.contributionDamage() > 0
                ? contribution.lastCreditIncrease() : contribution.firstParticipation()).orElseThrow();
    }

    /**
     * Returns the exact retained immutable completion, including optional full selection.
     * @return the identical immutable completion from which this ranking was constructed
     */
    public EncounterResult result() { return result; }
    /**
     * Returns immutable rows in placement order; later session/name/equipment changes cannot reorder them.
     * @return immutable rows in unique placement order, independent of later names or sessions
     */
    public List<Placement> placements() { return placements; }
    /**
     * Returns the frozen row for the supplied UUID, or empty for a nonparticipant (including null).
     * @param playerId participant UUID; null is treated as a nonparticipant
     * @return frozen placement row, or empty when the UUID did not participate
     */
    public Optional<Placement> placement(UUID playerId) {
        return placements.stream().filter(row -> row.playerId().equals(playerId)).findFirst();
    }
}
