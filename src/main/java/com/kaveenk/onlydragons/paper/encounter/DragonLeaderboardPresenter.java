package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.application.LeaderboardMessages;
import com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/** Server-thread, generation-scoped presentation. Retains at most one frozen board. */
public final class DragonLeaderboardPresenter {
    private final Function<UUID, String> names;
    private final BiConsumer<UUID, List<String>> send;
    private UUID generation;
    private RankedEncounterResult ranking;
    private int deliveryFailures;

    public DragonLeaderboardPresenter() {
        this(id -> {
            var player = Bukkit.getPlayer(id);
            return player == null ? id.toString() : player.getName();
        }, (id, lines) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) lines.forEach(line -> player.sendMessage(Component.text(line)));
        });
    }

    /** Injectable presentation boundary for behavior tests; never supplies score or identity. */
    public DragonLeaderboardPresenter(Function<UUID, String> names, BiConsumer<UUID, List<String>> send) {
        this.names = Objects.requireNonNull(names); this.send = Objects.requireNonNull(send);
    }

    public void begin(UUID generation) {
        thread(); this.generation = Objects.requireNonNull(generation); ranking = null;
    }

    public void retire(UUID generation) {
        thread(); if (Objects.equals(this.generation, generation)) this.generation = null;
    }

    public void accept(DevelopmentDragonService.Completion completion) {
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

    public Optional<RankedEncounterResult> ranking() { thread(); return Optional.ofNullable(ranking); }
    public int deliveryFailures() { thread(); return deliveryFailures; }
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Leaderboard requires server thread");
    }
}
