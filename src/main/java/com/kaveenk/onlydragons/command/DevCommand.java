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

    public DevCommand(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> sender.sendMessage(Component.text("OnlyDragons ready | version "
                    + plugin.getPluginMeta().getVersion()));
            case "reload" -> {
                if (!sender.hasPermission("onlydragons.admin")) {
                    sender.sendMessage(Component.text("You do not have permission to reload OnlyDragons."));
                    return true;
                }
                plugin.reloadSettings();
                sender.sendMessage(Component.text("OnlyDragons configuration reloaded."));
            }
            default -> sender.sendMessage(Component.text("Usage: /" + label + " [status|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        var options = sender.hasPermission("onlydragons.admin") ? List.of("status", "reload") : List.of("status");
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
