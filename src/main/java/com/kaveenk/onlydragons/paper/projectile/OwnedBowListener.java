package com.kaveenk.onlydragons.paper.projectile;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/** Event routing only; service owns state, suppression and the single scheduler. */
public final class OwnedBowListener implements Listener {
    private final OwnedBowService service;
    public OwnedBowListener(OwnedBowService service) { this.service = service; }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drawn(EntityShootBowEvent event) { service.drawn(event); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void launch(ProjectileLaunchEvent event) { service.launched(event); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getAction().isRightClick()) service.interact(event);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void stop(PlayerStopUsingItemEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void slot(PlayerItemHeldEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void swap(PlayerSwapHandItemsEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void drop(PlayerDropItemEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventory(InventoryOpenEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventoryClick(InventoryClickEvent event) { service.stopUsing(event.getWhoClicked().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventoryDrag(InventoryDragEvent event) { service.stopUsing(event.getWhoClicked().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void hit(ProjectileHitEvent event) { service.hit(event); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) service.damage(byEntity);
        service.protectTarget(event);
    }
    @EventHandler public void join(PlayerJoinEvent event) { service.activate(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        service.currentSession(event.getPlayer().getUniqueId()).ifPresent(token -> service.clearSession(event.getPlayer().getUniqueId(), token, false));
    }
    @EventHandler public void targetDeath(EntityDeathEvent event) { service.targetDied(event.getEntity()); }
    @EventHandler public void death(PlayerDeathEvent event) {
        service.ownerDied(event);
    }
}
