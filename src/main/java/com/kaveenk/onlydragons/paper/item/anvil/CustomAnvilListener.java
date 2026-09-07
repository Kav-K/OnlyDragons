package com.kaveenk.onlydragons.paper.item.anvil;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CustomAnvilListener implements Listener {
    private final CustomAnvilService anvils;
    public CustomAnvilListener(CustomAnvilService anvils) { this.anvils = anvils; }
    @EventHandler(priority = EventPriority.LOWEST) public void entering(InventoryClickEvent event) { anvils.entering(event); }
    @EventHandler(priority = EventPriority.HIGHEST) public void prepare(PrepareAnvilEvent event) { anvils.prepare(event); }
    @EventHandler(priority = EventPriority.HIGHEST) public void collect(InventoryClickEvent event) { anvils.collect(event); }
    @EventHandler public void close(InventoryCloseEvent event) { anvils.leave(event.getPlayer().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent event) { anvils.leave(event.getPlayer().getUniqueId()); }
}
