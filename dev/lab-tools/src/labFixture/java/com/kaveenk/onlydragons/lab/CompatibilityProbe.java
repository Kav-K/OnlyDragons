package com.kaveenk.onlydragons.lab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** External-plugin fixture for testing the lab pipeline across server versions. */
public class CompatibilityProbe extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("COMPATIBILITY_PROBE_OK");
        return true;
    }
}
