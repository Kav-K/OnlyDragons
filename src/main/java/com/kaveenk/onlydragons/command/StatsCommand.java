package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import com.kaveenk.onlydragons.application.PresentationFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parsing and display boundary for equipment inspection and session-only bonuses.
 * {@link EquipmentStatsService} owns validation and arithmetic; generated chat never
 * becomes an input to stats. Invoke commands on the classic Paper server thread.
 */
public final class StatsCommand {
    private final EquipmentStatsService stats;
    /**
     * Binds the command to its existing authority; does not register listeners or tasks.
     * @param stats server-thread equipment/snapshot authority
     */
    public StatsCommand(EquipmentStatsService stats) { this.stats = stats; }
    /**
     * Checks stats/calibration permission and player ownership before inspection or
     * a development edit. Bonus candidates validate atomically; a full inventory
     * rejects a loadout grant without replacing or dropping existing items.
     * @param sender requester and feedback recipient
     * @param args nonempty root arguments starting with stats or dev
     */
    public void execute(CommandSender sender, String[] args) {
        boolean dev = args[0].equalsIgnoreCase("dev");
        if (!sender.hasPermission(dev ? "onlydragons.calibration" : "onlydragons.stats")) {
            say(sender, "You do not have permission to " + (dev ? "use calibration tools." : "inspect stats."));
            return;
        }
        if (!(sender instanceof Player player)) { say(sender, "This command requires a player."); return; }
        if (!dev) {
            if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("explain"))) {
                say(sender, "Usage: /onlydragons stats [explain]"); return;
            }
            show(player, args.length == 2); return;
        }
        try {
            if (args.length == 3 && args[1].equalsIgnoreCase("loadout")) {
                var item = stats.createLoadout(args[2].toLowerCase(Locale.ROOT));
                int slot = -1;
                var storage = player.getInventory().getStorageContents();
                for (int i = 0; i < storage.length; i++) {
                    if (storage[i] == null || storage[i].getType().isAir()) { slot = i; break; }
                }
                if (slot < 0) { say(sender, "Inventory full; no loadout granted."); return; }
                player.getInventory().setItem(slot, item);
                stats.refresh(player);
                sender.sendMessage(PresentationFormatter.heading("Loadout · " + PresentationFormatter.label(args[2])));
                say(sender, "Granted " + args[2] + "; equip it in your main hand and use /onlydragons stats explain.");
            } else if (args.length == 4 && args[1].equalsIgnoreCase("bonus")) {
                var key = Arrays.stream(StatKey.values()).filter(k -> k.id().equals(args[2])).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown stat key"));
                stats.bonus(player, key, Double.parseDouble(args[3]));
                say(sender, "Session bonus replaced for " + key.id() + ". Clears on death, quit or dev clear.");
            } else if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
                stats.clearBonuses(player.getUniqueId()); stats.refresh(player);
                say(sender, "Session bonuses cleared.");
            } else say(sender, "Usage: /onlydragons dev loadout <id> | bonus <stat> <nonnegative amount> | clear");
        } catch (IllegalArgumentException invalid) { say(sender, "Invalid calibration request: " + invalid.getMessage()); }
    }
    /**
     * Revalidates both hands before emitting effective totals or full source provenance.
     * Offhand identity is diagnostic only; it supplies no active weapon modifiers.
     * @param player owner of the inspected equipment and output
     * @param explain whether to include raw values, revision and arithmetic sources
     */
    private void show(Player player, boolean explain) {
        var inspection = stats.refresh(player);
        var snapshot = inspection.stats().snapshot();
        if (!inspection.notice().isEmpty()) say(player, inspection.notice());
        player.sendMessage(PresentationFormatter.heading("Your stats"));
        if (!explain) {
            for (var key : StatKey.values()) player.sendMessage(PresentationFormatter.value(PresentationFormatter.label(key.id()), snapshot.effective(key)));
            player.sendMessage(PresentationFormatter.value("Critical hit chance (%)", snapshot.ordinaryCritProbability() * 100));
            return;
        }
        say(player, "Stats | profile " + inspection.stats().profileRevision() + " | revision " + snapshot.revision());
        say(player, "Main hand: " + describe(inspection.fingerprint().mainHand())
                + " | Offhand (inactive): " + describe(inspection.fingerprint().offHand()));
        for (var key : StatKey.values()) {
            say(player, key.id() + ": raw=" + snapshot.raw(key) + " effective=" + snapshot.effective(key));
            if (explain) {
                var detail = inspection.stats().explanations().get(key);
                say(player, "  base=" + detail.base());
                for (var step : detail.steps()) {
                    say(player, "  " + step.stage() + " operand=" + step.operand() + " result=" + step.result());
                    for (var source : step.contributions()) say(player, "    " + source.sourceId() + " "
                            + source.operation() + " " + source.amount());
                }
            }
        }
        say(player, "Ordinary crit probability=" + snapshot.ordinaryCritProbability());
    }
    /**
     * Renders typed codec outcomes without trusting item names or lore.
     * @param result validated, invalid or unmanaged hand classification
     * @return diagnostic identity/selections or the explicit invalid/unmanaged reason
     */
    private String describe(ItemReadResult result) {
        return switch (result) {
            case ItemReadResult.Valid valid -> valid.item().instance().identity().definitionId() + " "
                    + valid.item().instance().identity().instanceId() + " enchants=" + valid.item().instance().enchantLevels()
                    + " rolls=" + valid.item().instance().rolledModifierIds();
            case ItemReadResult.Invalid invalid -> "invalid " + invalid.code() + ": " + invalid.reason();
            case ItemReadResult.NotManaged ignored -> "no validated weapon";
        };
    }
    /**
     * Lists permitted stat/development subcommands and trusted loadout/stat identifiers.
     * @param sender requester used for permission filtering
     * @param args nonempty root arguments including the partial token
     * @return non-null candidate list, possibly empty; candidates are not prefix-filtered here
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("stats")) return sender.hasPermission("onlydragons.stats") && args.length == 2
                ? List.of("explain") : List.of();
        if (!args[0].equalsIgnoreCase("dev") || !sender.hasPermission("onlydragons.calibration")) return List.of();
        if (args.length == 2) return List.of("loadout", "bonus", "clear");
        if (args.length == 3 && args[1].equalsIgnoreCase("loadout")) return stats.loadouts();
        if (args.length == 3 && args[1].equalsIgnoreCase("bonus")) return Arrays.stream(StatKey.values()).map(StatKey::id).toList();
        return List.of();
    }
    /**
     * Sends semantic command text through the shared Adventure formatter.
     * @param sender command recipient
     * @param text plain semantic content; formatting does not change command state
     */
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
