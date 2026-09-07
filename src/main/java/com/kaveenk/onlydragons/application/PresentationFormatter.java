package com.kaveenk.onlydragons.application;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Display only. Never feed rounded values or rendered names back into gameplay. */
public final class PresentationFormatter {
    private PresentationFormatter() {}

    /**
     * Returns Roman I–X for levels 1–10; throws IllegalArgumentException outside that display range.
     */
    public static String roman(int level) {
        if (level < 1 || level > 10) throw new IllegalArgumentException("Display level must be I–X");
        return new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[level - 1];
    }

    /**
     * Formats one nonnull trusted modifier with stat label, sign and unit. FLAT chance/damage/speed
     * percent stats use %, ADDITIVE_PERCENT uses %, and factors use ×. Does not aggregate or resolve stats.
     */
    public static String statModifier(com.kaveenk.onlydragons.domain.stats.StatModifier modifier) {
        String amount = number(modifier.amount());
        String value = switch (modifier.operation()) {
            case MULTIPLIER -> "×" + amount;
            case ADDITIVE_PERCENT -> (modifier.amount() >= 0 ? "+" : "") + amount + "%";
            case FLAT -> (modifier.amount() >= 0 ? "+" : "") + amount
                    + switch (modifier.key()) {
                        case CRIT_CHANCE, CRIT_DAMAGE, ATTACK_SPEED -> "%";
                        case MAX_HEALTH -> " HP";
                        default -> "";
                    };
        };
        return label(modifier.key().id()) + ": " + value;
    }

    /**
     * Formats a finite number with Locale.ROOT grouping and up to two decimals; signed zero becomes
     * "0". A fresh DecimalFormat avoids shared mutable formatter state. Non-finite input rejects.
     */
    public static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite display value");
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value == 0 ? 0 : value);
    }

    /**
     * Formats a finite full-precision credit value with grouping and exactly two decimals; signed
     * zero becomes "0.00". The returned rounded string must never participate in ranking.
     */
    public static String credit(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite display value");
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value == 0 ? 0 : value);
    }

    /**
     * Converts a nonnull machine identifier to title words using Locale.ROOT, replacing underscores
     * and hyphens with spaces. Blank input may yield empty output; this is not identity validation.
     */
    public static String label(String id) {
        String[] words = id.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').split(" +");
        var result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    /**
     * Returns literal title text in bold gold; no markup parsing, message delivery or gameplay mutation.
     */
    public static Component heading(String title) {
        return Component.text(title, NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    /**
     * Returns a gray label and aqua finite numeric value using {@link #number(double)}.
     */
    public static Component value(String label, double amount) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(number(amount), NamedTextColor.AQUA));
    }

    /**
     * Returns health/max clamped to [0,1] and narrowed to float for Adventure. Both inputs must
     * be finite and max strictly positive; negative/over-max health is clamped for display only.
     * No domain HP validation or mutation occurs.
     */
    public static float healthProgress(double health, double max) {
        if (!Double.isFinite(health) || !Double.isFinite(max) || max <= 0)
            throw new IllegalArgumentException("Invalid display health");
        return (float) Math.max(0, Math.min(1, health / max));
    }

    /**
     * Returns a literal heading, displayed HP/max and clamped percentage. Validates via
     * {@link #healthProgress(double,double)}; the printed HP numbers retain the supplied values.
     */
    public static Component healthTitle(String name, double health, double max) {
        healthProgress(health, max);
        return heading(name).append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(number(health) + " / " + number(max) + " HP", NamedTextColor.RED))
                .append(Component.text("  (" + number(Math.max(0, Math.min(1, health / max)) * 100) + "%)", NamedTextColor.WHITE));
    }

    /**
     * Renders source kind, captured ordinary crit, actual HP removed, credited damage and supplied
     * remaining domain HP. It neither applies results nor establishes that the hit was accepted.
     */
    public static Component combat(com.kaveenk.onlydragons.domain.combat.DamageResult damage, double remaining) {
        return heading(label(damage.kind().name()) + " · " + label(damage.crit().name()))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(value("HP removed", damage.amounts().actualHealthDamage()))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(value("Credit", damage.amounts().contributionDamage()))
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(value("HP left", remaining));
    }

    /**
     * Wraps literal text in gray without parsing markup or sending it to an audience.
     */
    public static Component message(String text) { return Component.text(text, NamedTextColor.GRAY); }

    /**
     * Colors the existing LeaderboardMessages text protocol: gold heading, aqua personal result,
     * or yellow placement/name and white credit. Unknown text falls back to gray; no ranking occurs.
     */
    public static Component leaderboard(String text) {
        if (text.startsWith("Dragon defeated!")) return heading(text);
        if (text.startsWith("Your placement:")) return Component.text(text, NamedTextColor.AQUA);
        int separator = text.indexOf(" - ");
        if (separator < 0) return message(text);
        return Component.text(text.substring(0, separator), NamedTextColor.YELLOW)
                .append(Component.text(text.substring(separator), NamedTextColor.WHITE));
    }
}
