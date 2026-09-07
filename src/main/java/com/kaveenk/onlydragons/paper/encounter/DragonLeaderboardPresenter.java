package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.application.LeaderboardMessages;
import com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/**
 * Server-thread, generation-scoped ranking delivery retaining at most one frozen board.
 * Package-private mutation prevents consumers from resetting deduplication. Names are
 * display-only, never sorting inputs. Offline participants stay ranked, with no queue
 * or persistent history; a recipient failure does not retry the public announcement.
 */
final class DragonLeaderboardPresenter {
    private final Function<UUID, String> names;
    private final BiConsumer<UUID, List<String>> send;
    private UUID generation;
    private RankedEncounterResult ranking;
    private int deliveryFailures;

    /**
     * Uses current Bukkit names with UUID fallback and sends only to currently connected participants.
     */
    DragonLeaderboardPresenter() {
        this(id -> {
            var player = Bukkit.getPlayer(id);
            return player == null ? id.toString() : player.getName();
        }, (id, lines) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) lines.forEach(line -> player.sendMessage(com.kaveenk.onlydragons.application.PresentationFormatter.leaderboard(line)));
        });
    }

    /**
     * Injects presentation-only lookups/sinks for tests without supplying score or identity.
     * @param names non-null name resolver; ranking still uses stable UUID/provenance
     * @param send non-null per-participant immutable message-list sink
     * @throws NullPointerException if either presentation boundary is null
     */
    DragonLeaderboardPresenter(Function<UUID, String> names, BiConsumer<UUID, List<String>> send) {
        this.names = Objects.requireNonNull(names); this.send = Objects.requireNonNull(send);
    }

    /**
     * Replaces the presentation generation and forgets the previous retained board.
     * @param generation non-null successful spawn UUID
     */
    void begin(UUID generation) {
        thread(); this.generation = Objects.requireNonNull(generation); ranking = null;
    }

    /**
     * Disables future delivery only for the matching generation, retaining its frozen board.
     * @param generation retiring token; stale tokens have no effect
     */
    void retire(UUID generation) {
        thread(); if (Objects.equals(this.generation, generation)) this.generation = null;
    }

    /**
     * Validates generation/native ANIMATING outcome and ranks frozen provenance once.
     * Marks the board before sending so reentrant/replayed delivery cannot announce twice.
     * Each participant receives top ten plus own placement; sender failures are isolated.
     * @param completion confirmed immutable completion from the development service
     * @throws IllegalArgumentException for malformed ranking provenance or a conflicting second completion
     * @throws IllegalStateException if called off the server thread
     */
    void accept(DevelopmentDragonService.Completion completion) {
        thread();
        if (generation == null || !generation.equals(completion.generation())
                || !generation.equals(completion.result().encounterId())
                || !"ANIMATING".equals(completion.nativeOutcome())) return;
        // One authoritative completion per generation. Mark before sending, including failures,
        // so reentrant/replayed delivery cannot repeat any public announcement.
        if (ranking != null && ranking.result().completionId().equals(completion.result().completionId())) return;
        if (ranking != null) throw new IllegalArgumentException("Generation already has a different completion");
        var candidate = new RankedEncounterResult(completion.result());
        var top = LeaderboardMessages.topTen(candidate, names);
        ranking = candidate;
        for (var row : candidate.placements()) {
            var lines = new ArrayList<>(top); lines.add(LeaderboardMessages.own(row));
            try { send.accept(row.playerId(), List.copyOf(lines)); }
            catch (RuntimeException failure) { deliveryFailures++; }
        }
    }

    /**
     * Reads the retained board independently from whether delivery has been retired.
     * @return empty before a qualifying completion or after the next begin
     */
    Optional<RankedEncounterResult> ranking() { thread(); return Optional.ofNullable(ranking); }
    /**
     * Reports failed participant sends without implying a failed accounting commit.
     * @return cumulative isolated recipient failures
     */
    int deliveryFailures() { thread(); return deliveryFailures; }
    /**
     * Enforces synchronous native-name lookup and recipient delivery ownership.
     */
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Leaderboard requires server thread");
    }
}
