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

    public DevCommand(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
        this.stats = new StatsCommand(plugin.equipment());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            default -> sender.sendMessage(Component.text("Usage: /" + label + " [status|reload|stats|dev]"));
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
            if (sender.hasPermission("onlydragons.calibration")) available.add("dev");
            options = available;
        } else options = stats.complete(sender, args);
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
