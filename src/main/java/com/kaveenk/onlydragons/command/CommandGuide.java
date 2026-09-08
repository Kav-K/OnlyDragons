package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Immutable command descriptions and short-route translation. Visibility samples
 * the caller's current permissions and sender type on each request; it grants no
 * authority. Existing delegates remain responsible for validation and mutation.
 */
final class CommandGuide {
    private record Entry(String topic, String syntax, String explanation, String permission, boolean playerOnly) {
        boolean visible(CommandSender sender) {
            return sender.hasPermission("onlydragons.use") && sender.hasPermission(permission)
                    && (!playerOnly || sender instanceof Player);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("help", "help [topic]", "Show commands available to you.", "onlydragons.use", false),
            new Entry("status", "status", "Show plugin readiness and version (also the empty command).", "onlydragons.use", false),
            new Entry("reload", "reload", "Reload greeting/configuration settings.", "onlydragons.admin", false),
            new Entry("stats", "stats [explain]", "Inspect equipment totals or their sources.", "onlydragons.stats", true),
            new Entry("combat", "combat last", "Inspect your last captured combat hit.", "onlydragons.combat", true),
            new Entry("dragon", "dragon status", "Inspect the configured arena and current dragon.", "onlydragons.practice", false),
            new Entry("dragon", "dragon setup <world-key> <x> <y> <z> <radius 16-48> test_dragon", "Save the development arena; coordinates/radius are blocks.", "onlydragons.practice", false),
            new Entry("dragon", "dragon spawn [standard|training|calibration] [orbit|stationary]", "Spawn a managed dragon; defaults to standard orbit.", "onlydragons.practice", false),
            new Entry("dragon", "dragon reset [generation]", "Remove the current matching dragon generation.", "onlydragons.practice", false),
            new Entry("dragon", "dragon result [generation]", "Inspect the frozen result of a completed generation.", "onlydragons.practice", false),
            new Entry("bow", "bow [help|list]", "Explain training setup or list training bow defaults.", "onlydragons.calibration", false),
            new Entry("bow", "bow kit", "Grant seven training bows and 512 arrows; requires 15 empty storage slots.", "onlydragons.calibration", true),
            new Entry("bow", "bow give <id>", "Grant one catalog loadout in an empty storage slot; no arrows.", "onlydragons.calibration", true),
            new Entry("book", "book <enchant> <level>", "Grant one custom enchant book for anvil use; requires an empty slot.", "onlydragons.calibration", true),
            new Entry("practice", "practice kit <loadout>", "Grant one loadout and 64 arrows; requires two empty storage slots.", "onlydragons.practice", true),
            new Entry("practice", "practice dummy <full|reduced|score-only> [hp]", "Create your practice target; HP defaults to 1000, range 1-1000000.", "onlydragons.practice", true),
            new Entry("practice", "practice scenario <full|reduced|score-only> [hp]", "Create a target with controlled fractional proc samples; same HP range.", "onlydragons.practice", true),
            new Entry("practice", "practice reset", "Remove your practice target.", "onlydragons.practice", true),
            new Entry("dev", "dev bonus <stat> <nonnegative amount>", "Replace a session stat bonus; cleared on death or quit.", "onlydragons.calibration", true),
            new Entry("dev", "dev clear", "Clear your session stat bonuses.", "onlydragons.calibration", true),
            new Entry("dev", "dev loadout <id>", "Legacy bow give route; all existing dev routes remain available.", "onlydragons.calibration", true),
            new Entry("dev", "dev dragon ...", "Legacy dragon route; see help dragon for exact actions.", "onlydragons.practice", false),
            new Entry("dev", "dev shortbow [help|list]", "Legacy bow help/list route.", "onlydragons.calibration", false),
            new Entry("dev", "dev shortbow kit", "Legacy bow kit route.", "onlydragons.calibration", true),
            new Entry("dev", "dev book <enchant> <level>", "Legacy book grant route.", "onlydragons.calibration", true),
            new Entry("dev", "dev kit <loadout> | dev dummy <profile> [hp] | dev scenario <profile> [hp] | dev reset", "Legacy practice routes; see help practice for profiles.", "onlydragons.practice", true));

    private CommandGuide() { }

    /** Returns distinct visible topics in display order, with no player-name fallback. */
    static List<String> topics(CommandSender sender) {
        return ENTRIES.stream().filter(e -> e.visible(sender)).map(Entry::topic).distinct().toList();
    }

    /** Displays exact permitted syntax for one topic, or a compact topic index. */
    static void show(CommandSender sender, String topic) {
        if (topic.isEmpty()) {
            sender.sendMessage(PresentationFormatter.heading("OnlyDragons help"));
            sender.sendMessage(PresentationFormatter.message("/onlydragons help <topic> · " + String.join(", ", topics(sender))));
            sender.sendMessage(PresentationFormatter.message("Use /onlydragons help status for readiness. /mcdev is an alias."));
            return;
        }
        var entries = ENTRIES.stream().filter(e -> e.topic().equalsIgnoreCase(topic) && e.visible(sender)).toList();
        if (entries.isEmpty()) {
            sender.sendMessage(PresentationFormatter.message("No available help for '" + topic + "'. Use /onlydragons help."));
            return;
        }
        sender.sendMessage(PresentationFormatter.heading("OnlyDragons · " + topic.toLowerCase(Locale.ROOT)));
        entries.forEach(e -> sender.sendMessage(PresentationFormatter.message("/onlydragons " + e.syntax()
                + " · " + e.explanation() + " [" + (e.playerOnly() ? "player only" : "player/console") + "]")));
    }

    /** Translates only the short namespaces; returned arguments still require delegate authorization. */
    static String[] translate(String[] args) {
        if (args.length == 0) return args;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "dragon", "book" -> prependDev(args);
            case "practice" -> replace(args, "dev");
            case "bow" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("give")) {
                    var translated = args.clone(); translated[0] = "dev"; translated[1] = "loadout"; yield translated;
                }
                yield prependDev(replace(args, "shortbow"));
            }
            default -> args;
        };
    }

    private static String[] replace(String[] args, String root) {
        var copy = args.clone(); copy[0] = root; return copy;
    }

    private static String[] prependDev(String[] args) {
        var copy = new String[args.length + 1]; copy[0] = "dev";
        System.arraycopy(args, 0, copy, 1, args.length); return copy;
    }
}
