package com.kaveenk.onlydragons.paper.item.anvil;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ordered native-anvil ingress; no handler charges XP or consumes either input.
 * LOWEST captures the exact offer before other click handlers can replace inputs;
 * HIGHEST validates extraction while the service later observes actual native completion.
 * All handlers are synchronous and include cancelled events for explicit rejection.
 * @see CustomAnvilService
 */
public final class CustomAnvilListener implements Listener {
    private final CustomAnvilService anvils;
    /**
     * Routes events to the plugin-owned transaction guard.
     * @param anvils active server-thread service, registered/closed by the composition root
     */
    public CustomAnvilListener(CustomAnvilService anvils) { this.anvils = anvils; }
    /**
     * Captures the entering result-slot offer before later input replacement.
     * @param event synchronous click, including already cancelled events
     */
    @EventHandler(priority = EventPriority.LOWEST) public void entering(InventoryClickEvent event) { anvils.entering(event); }
    /**
     * Publishes the isolated custom preview before native extraction can debit it.
     * @param event native prepare event; ordinary recipes remain untouched
     */
    @EventHandler(priority = EventPriority.HIGHEST) public void prepare(PrepareAnvilEvent event) { anvils.prepare(event); }
    /**
     * Validates the same event's captured offer at the final supported priority.
     * @param event native click; rejected managed extraction is cancelled
     */
    @EventHandler(priority = EventPriority.HIGHEST) public void collect(InventoryClickEvent event) { anvils.collect(event); }
    /**
     * Invalidates the closed view so deferred work cannot restore its old result.
     * @param event actual inventory close event
     */
    @EventHandler public void close(InventoryCloseEvent event) { anvils.leave(event.getPlayer().getUniqueId()); }
    /**
     * Invalidates disconnected player state before any deferred transaction observation.
     * @param event actual player quit event
     */
    @EventHandler public void quit(PlayerQuitEvent event) { anvils.leave(event.getPlayer().getUniqueId()); }
}
