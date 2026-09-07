package com.kaveenk.onlydragons.paper.encounter;

import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lifecycle/healing/reward boundary only; {@link com.kaveenk.onlydragons.paper.projectile.OwnedBowService} remains the sole
 * physical candidate and native-damage authority. Final native death observation at
 * MONITOR is separate from HIGHEST drop/XP suppression and administrative retirement.
 * @see ManagedCombatService
 */
public final class ManagedCombatListener implements Listener {
    private final ManagedCombatService service;
    /**
     * Routes synchronous lifecycle events to the existing accounting owner.
     * @param service plugin-lifetime managed combat service
     */
    public ManagedCombatListener(ManagedCombatService service) { this.service = service; }
    /**
     * Removes session-specific proc/fire state while retaining already airborne ownership.
     * @param event actual player quit at MONITOR
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { service.playerEnded(event.getPlayer().getUniqueId()); }
    /**
     * Clears the exact activated player session independently of other listener ordering.
     * @param event actual death; the inherited player UUID identifies the session
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent event) { service.playerEnded(event.getPlayer().getUniqueId()); }
    /**
     * Clears owned-target drops and XP without cancelling native death.
     * Unexpected ACTIVE target death retires the encounter; domain-defeated animation
     * retains its native owner until actual removal.
     * @param event native death event, including cancelled events
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (service.ownsEntity(event.getEntity().getUniqueId())) {
            event.getDrops().clear(); event.setDroppedExp(0);
            service.entityEnded(event.getEntity().getUniqueId());
        }
    }
    /**
     * Forwards final native cancellation to the backend without minting a second result.
     * @param event native death observed at MONITOR
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void deathObserved(EntityDeathEvent event) { service.nativeDeathObserved(event.getEntity().getUniqueId(), event.isCancelled()); }
    /**
     * Forwards actual removal cause rather than treating liveness as removal.
     * @param event immediate public entity-removal event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(EntityRemoveEvent event) { service.nativeRemoved(event.getEntity().getUniqueId(), event.getCause()); }
    /**
     * Rejects unmanaged native healing while the target retains managed ownership.
     * @param event attempted native healing; unrelated entities remain untouched
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void heal(EntityRegainHealthEvent event) {
        if (service.ownsEntity(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
}
