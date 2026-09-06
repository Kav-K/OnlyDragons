package com.kaveenk.onlydragons.paper.encounter;

import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;

/** Lifecycle only; T06 remains the sole physical/native-damage boundary. */
public final class ManagedCombatListener implements Listener {
    private final ManagedCombatService service;
    public ManagedCombatListener(ManagedCombatService service) { this.service = service; }
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { service.playerEnded(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent event) { service.playerEnded(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (service.ownsEntity(event.getEntity().getUniqueId())) {
            event.getDrops().clear(); event.setDroppedExp(0);
            service.entityEnded(event.getEntity().getUniqueId());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void heal(EntityRegainHealthEvent event) {
        if (service.ownsEntity(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
}
