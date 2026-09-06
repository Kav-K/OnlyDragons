package com.kaveenk.onlydragons.domain.encounter;

import java.util.*;

/** Immutable placement projection of one authoritative completion; no damage or reward authority. */
public final class RankedEncounterResult {
    public record Placement(int place, UUID playerId, EncounterResult.Contribution contribution) {}

    private final EncounterResult result;
    private final List<Placement> placements;

    public RankedEncounterResult(EncounterResult result) {
        this.result = Objects.requireNonNull(result);
        if (result.completedOrdinal() < 1 || result.participants().isEmpty())
            throw new IllegalArgumentException("Ranking requires production completion provenance");
        // A commit ordinal belongs to exactly one participant and tick. First participation
        // and last increase may describe the same commit, but cannot disagree about it.
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
        boolean terminal = result.participants().values().stream().anyMatch(contribution ->
                contribution.lastCreditIncrease().filter(stamp -> stamp.ordinal() == result.completedOrdinal()
                        && stamp.tick() == result.completedTick()).isPresent());
        if (!terminal) throw new IllegalArgumentException("Missing lethal commit provenance");
        var ordered = new ArrayList<>(result.participants().entrySet());
        ordered.sort(Comparator.<Map.Entry<UUID, EncounterResult.Contribution>>comparingDouble(
                        entry -> normalizedCredit(entry.getValue())).reversed()
                .thenComparingLong(entry -> stamp(entry.getValue()).tick())
                .thenComparingLong(entry -> stamp(entry.getValue()).ordinal()));
        var ranked = new ArrayList<Placement>();
        for (var entry : ordered) ranked.add(new Placement(ranked.size() + 1, entry.getKey(), entry.getValue()));
        placements = List.copyOf(ranked);
    }

    private static double normalizedCredit(EncounterResult.Contribution contribution) {
        return contribution.contributionDamage() == 0 ? 0 : contribution.contributionDamage();
    }

    private static EncounterResult.CommitStamp stamp(EncounterResult.Contribution contribution) {
        return (contribution.contributionDamage() > 0
                ? contribution.lastCreditIncrease() : contribution.firstParticipation()).orElseThrow();
    }

    public EncounterResult result() { return result; }
    public List<Placement> placements() { return placements; }
    public Optional<Placement> placement(UUID playerId) {
        return placements.stream().filter(row -> row.playerId().equals(playerId)).findFirst();
    }
}
