package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Synchronous root-command router shared by the primary name and its aliases.
 * Specific development namespaces run before generic stats/dev handling. Each
 * delegate enforces its own permission and player-only boundary.
 * @see OnlyDragonsPlugin#onEnable()
 */
public final class DevCommand implements CommandExecutor, TabCompleter {
    private final OnlyDragonsPlugin plugin;
    private final StatsCommand stats;
    private final PracticeCommand practice;
    private final DragonCommand dragons;
    private final ShortbowCommand shortbows;
    private final BookCommand books;

    /**
     * Binds delegates to an already constructed plugin service graph.
     * @param plugin enabled composition root; its gameplay services must exist
     */
    public DevCommand(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
        this.stats = new StatsCommand(plugin.equipment());
        this.practice = new PracticeCommand(plugin);
        this.dragons = new DragonCommand(plugin.dragons());
        this.shortbows = new ShortbowCommand(new com.kaveenk.onlydragons.paper.item.equipment.ShortbowKitService(plugin.equipment()));
        this.books = new BookCommand(com.kaveenk.onlydragons.domain.item.CalibrationLoadouts.fireRegistry());
    }

    /**
     * Dispatches a request and emits its own usage/error messages without Bukkit fallback.
     * Reload is greeting/configuration reload only, never a gameplay service reload.
     * @param sender invoking console or player, used for permission checks and replies
     * @param command Bukkit command registration; aliases share the same routing
     * @param label actual invoked alias, used in fallback usage
     * @param args non-null arguments after the root command; empty selects status
     * @return always true because every branch handles its own response
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (shortbows.handles(args)) { shortbows.execute(sender, args); return true; }
        if (books.handles(args)) { books.execute(sender, args); return true; }
        if (dragons.handles(args)) { dragons.execute(sender, args); return true; }
        if (args.length > 0 && practice.handles(args)) { practice.execute(sender, args); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> sender.sendMessage(Component.text("OnlyDragons ready | version "
                    + plugin.getPluginMeta().getVersion()));
            case "stats", "dev" -> stats.execute(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("onlydragons.admin")) {
                    sender.sendMessage(Component.text("You do not have permission to reload OnlyDragons."));
                    return true;
                }
                plugin.reloadSettings();
                sender.sendMessage(Component.text("OnlyDragons configuration reloaded."));
            }
            default -> sender.sendMessage(Component.text("Usage: /" + label + " [status|reload|stats|combat|dev]"));
        }
        return true;
    }

    /**
     * Combines permission-filtered delegate candidates, then filters the final prefix.
     * @param sender requester whose permissions determine visible namespaces
     * @param command Bukkit command registration
     * @param alias invoked root alias; candidates are independent of its spelling
     * @param args non-null partial arguments; empty returns no candidates
     * @return non-null prefix-matching suggestions; never delegates to player-name completion
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        List<String> options;
        if (args.length == 1) {
            var available = new java.util.ArrayList<String>();
            available.add("status");
            if (sender.hasPermission("onlydragons.admin")) available.add("reload");
            if (sender.hasPermission("onlydragons.stats")) available.add("stats");
            if (sender.hasPermission("onlydragons.combat")) available.add("combat");
            if (sender.hasPermission("onlydragons.calibration") || sender.hasPermission("onlydragons.practice")) available.add("dev");
            options = available;
        } else {
            var combined = new java.util.ArrayList<>(stats.complete(sender, args));
            combined.addAll(practice.complete(sender, args)); combined.addAll(dragons.complete(sender, args));
            combined.addAll(shortbows.complete(sender, args));
            combined.addAll(books.complete(sender, args));
            options = combined;
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
