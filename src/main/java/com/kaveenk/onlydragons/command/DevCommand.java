package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class DevCommand implements CommandExecutor, TabCompleter {
    private final OnlyDragonsPlugin plugin;
    private final StatsCommand stats;
    private final PracticeCommand practice;
    private final DragonCommand dragons;
    private final BookCommand books;

    public DevCommand(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
        this.stats = new StatsCommand(plugin.equipment());
        this.practice = new PracticeCommand(plugin);
        this.dragons = new DragonCommand(plugin.dragons());
        this.books = new BookCommand(com.kaveenk.onlydragons.domain.item.CalibrationLoadouts.fireRegistry());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            combined.addAll(practice.complete(sender, args)); combined.addAll(dragons.complete(sender, args)); options = combined;
            combined.addAll(books.complete(sender, args));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
