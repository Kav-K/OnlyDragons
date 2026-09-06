package com.kaveenk.onlydragons.listener;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {
    private final OnlyDragonsPlugin plugin;

    public JoinListener(OnlyDragonsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("welcome-enabled", true)) {
            event.getPlayer().sendMessage(Component.text(plugin.greetings().welcome(event.getPlayer().getName())));
        }
        Bukkit.broadcast(Component.text("Testing"));
    }
}
