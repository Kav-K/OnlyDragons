package com.kaveenk.onlydragons.application;

import com.kaveenk.onlydragons.domain.encounter.RankedEncounterResult;
import java.util.*;
import java.util.function.Function;

/** Plain text formatting is separate from ranking and online-player delivery. */
public final class LeaderboardMessages {
    private LeaderboardMessages() {}

    /**
     * Returns an immutable header plus up to ten rows in existing placement order. The nonnull
     * name function is invoked only for visible rows; null/blank names fall back to UUID text.
     * Name-provider exceptions propagate. Formatting does not change full-precision rank.
     */
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

    /**
     * Returns the personal placement and fixed-two-decimal credit for a nonnull frozen row.
     * Delivery and participant eligibility belong to the presenter.
     */
    public static String own(RankedEncounterResult.Placement row) {
        return "Your placement: #" + row.place() + " - " + credit(row) + " credited damage";
    }

    /**
     * Uses the shared display-only credit formatter, never actual HP or a recomputed score.
     */
    private static String credit(RankedEncounterResult.Placement row) {
        double amount = row.contribution().contributionDamage();
        return PresentationFormatter.credit(amount);
    }
}
