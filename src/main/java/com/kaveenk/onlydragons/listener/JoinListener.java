package com.kaveenk.onlydragons.listener;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Starter synchronous join presentation, independent of combat session activation.
 * It sends the optional configured welcome to the joiner and retains the existing
 * global testing broadcast; neither message grants equipment or authorizes damage.
 */
public final class JoinListener implements Listener {
    private final OnlyDragonsPlugin plugin;

    /**
     * Uses the live plugin configuration and current greeting formatter at event time.
     * @param plugin constructed plugin whose greeting settings have been loaded
     */
    public JoinListener(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends join feedback using the current configuration on the server thread.
     * @param event actual join event; its player receives the optional welcome
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("welcome-enabled", true)) {
            event.getPlayer().sendMessage(Component.text(plugin.greetings().welcome(event.getPlayer().getName())));
        }
        Bukkit.broadcast(Component.text("Testing"));
    }
}
