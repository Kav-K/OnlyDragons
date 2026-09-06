package com.kaveenk.onlydragons.lab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only companion: verifies actual plugin state, not just startup log messages. */
public class LabHarness extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("MCDEV_CHECK_FAILED expected one plugin name");
            return true;
        }
        Plugin target = getServer().getPluginManager().getPlugin(args[0]);
        if (target == null || !target.isEnabled()) {
            sender.sendMessage("MCDEV_CHECK_FAILED " + args[0]);
        } else {
            sender.sendMessage("MCDEV_CHECK_OK " + target.getName() + " " + target.getDescription().getVersion());
        }
        return true;
    }
}
