package com.kaveenk.onlydragons.paper.projectile;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/**
 * Server-thread event routing for {@link OwnedBowService}; no duplicate registry or task.
 * Priorities intentionally separate early physical capture from final-dispatch inspection.
 * Lifecycle/input invalidation also observes cancelled events; native suppression is not
 * evidence that another plugin accepted the physical hit.
 */
public final class OwnedBowListener implements Listener {
    private final OwnedBowService service;
    /**
     * Connects routing to the plugin-owned firing lifetime.
     * @param service existing firing owner; must be wired before listener registration
     */
    public OwnedBowListener(OwnedBowService service) { this.service = service; }
    /**
     * At HIGHEST, delegates accepted-fire snapshotting and native shortbow-release suppression; cancelled events still reach the service.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drawn(EntityShootBowEvent event) { service.drawn(event); }
    /**
     * At HIGHEST, retains final launch-veto evidence for owned arrows and clears native damage effects.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void launch(ProjectileLaunchEvent event) { service.launched(event); }
    /**
     * At MONITOR, queues real click input without changing cancellation; the service checks final item-use denial next tick.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getAction().isRightClick()) service.interact(event);
    }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void stop(PlayerStopUsingItemEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void slot(PlayerItemHeldEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void swap(PlayerSwapHandItemsEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void drop(PlayerDropItemEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventory(InventoryOpenEvent event) { service.stopUsing(event.getPlayer().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventoryClick(InventoryClickEvent event) { service.stopUsing(event.getWhoClicked().getUniqueId()); }
    /**
     * At MONITOR, invalidates queued/held input even for cancelled events; it does not retire independently owned airborne arrows.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventoryDrag(InventoryDragEvent event) { service.stopUsing(event.getWhoClicked().getUniqueId()); }
    /**
     * At LOWEST, captures the first native collision; final cancellation is inspected after dispatch, not inferred here.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void hit(ProjectileHitEvent event) { service.hit(event); }
    /**
     * At HIGHEST, captures any pre-own arrow veto before suppressing native damage on owned arrows and protected targets.
     * @param event actual synchronous Paper event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) service.damage(byEntity);
        service.protectTarget(event);
    }
    /**
     * Reconciles an actual joining player with currently admitted arenas.
     * @param event actual synchronous Paper event
     */
    @EventHandler public void join(PlayerJoinEvent event) { service.activate(event.getPlayer()); }
    /**
     * Invalidates only the current session on actual quit, retaining an already launched primary and its settled charge.
     * @param event actual synchronous Paper event
     */
    @EventHandler public void quit(PlayerQuitEvent event) {
        service.currentSession(event.getPlayer().getUniqueId()).ifPresent(token -> service.clearSession(event.getPlayer().getUniqueId(), token, false));
    }
    /**
     * Closes firing admission when a registered native target dies; retained animation ownership belongs to the encounter backend.
     * @param event actual synchronous Paper event
     */
    @EventHandler public void targetDeath(EntityDeathEvent event) { service.targetDied(event.getEntity()); }
    /**
     * Removes all arrows of the actual dead owner and routes refundable ammo through native death drops when needed.
     * @param event actual synchronous Paper event
     */
    @EventHandler public void death(PlayerDeathEvent event) {
        service.ownerDied(event);
    }
}
