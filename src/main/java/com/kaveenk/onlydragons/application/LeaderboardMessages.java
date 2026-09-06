package com.kaveenk.onlydragons.application;

import com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult;
import java.util.*;
import java.util.function.Function;

/** Plain text formatting is separate from ranking and online-player delivery. */
public final class LeaderboardMessages {
    private LeaderboardMessages() {}

    public static List<String> topTen(RankedEncounterResult ranking, Function<UUID, String> names) {
        var lines = new ArrayList<String>();
        lines.add("Dragon defeated! Credited damage");
        for (var row : ranking.placements().stream().limit(10).toList()) {
            String name = names.apply(row.playerId());
            if (name == null || name.isBlank()) name = row.playerId().toString();
            lines.add("#" + row.place() + " " + name + " - " + credit(row) + " credited damage");
        }
        return List.copyOf(lines);
    }

    public static String own(RankedEncounterResult.Placement row) {
        return "Your placement: #" + row.place() + " - " + credit(row) + " credited damage";
    }

    private static String credit(RankedEncounterResult.Placement row) {
        double amount = row.contribution().contributionDamage();
        return PresentationFormatter.credit(amount);
    }
}
