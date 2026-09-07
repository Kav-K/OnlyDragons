package com.kaveenk.onlydragons.lab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Separate legacy-API lab companion that queries real enabled-plugin state.
 * Its compatibility classpath must remain isolated from the modern production
 * plugin. A positive response proves name/version/enabled state, not gameplay.
 */
public class LabHarness extends JavaPlugin {
    /**
     * Checks exactly one plugin name and emits the lab's stable machine-readable marker.
     * @param sender recipient of the check result
     * @param command registered lab command
     * @param label invoked alias
     * @param args one exact plugin name
     * @return true for both explicit success and explicit rejection
     */
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
