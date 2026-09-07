package com.kaveenk.onlydragons.lab;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal external-plugin artifact for exercising the lab across supported server versions.
 * Its command marker proves that this fixture loaded and dispatched; it does not
 * validate OnlyDragons mechanics or import the legacy API into production.
 */
public class CompatibilityProbe extends JavaPlugin {
    /**
     * Emits the fixed compatibility marker when the actual server dispatches the command.
     * @param sender result recipient
     * @param command registered fixture command
     * @param label invoked alias
     * @param args ignored fixture arguments
     * @return true after reporting the marker
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("COMPATIBILITY_PROBE_OK");
        return true;
    }
}
