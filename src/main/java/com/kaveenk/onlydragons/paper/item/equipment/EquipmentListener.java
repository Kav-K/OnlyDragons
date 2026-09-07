package com.kaveenk.onlydragons.paper.item.equipment;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Server-thread inventory observer that coalesces refreshes until mutations settle.
 * Quit/death cancel the pending task before clearing cache and bonuses. Delayed work
 * looks up the current live player by UUID; shot acceptance still refreshes directly.
 * @see EquipmentStatsService#refresh(Player)
 */
public final class EquipmentListener implements Listener {
    private final Plugin plugin;
    private final EquipmentStatsService stats;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    /**
     * Binds the scheduler owner and equipment cache without registering itself.
     * @param plugin owner of all next-tick tasks
     * @param stats cache refreshed or cleared by events
     */
    public EquipmentListener(Plugin plugin, EquipmentStatsService stats) { this.plugin = plugin; this.stats = stats; }

    /**
     * Observes a completed slot change without deriving stats from the event payload.
     * @param event actual synchronous inventory-slot notification
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void inventory(PlayerInventorySlotChangeEvent event) { queue(event.getPlayer()); }
    /**
     * Queues accepted held-slot changes at MONITOR after cancellation has settled.
     * @param event actual synchronous held-slot notification
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) { queue(event.getPlayer()); }
    /**
     * Queues accepted swaps at MONITOR; offhand remains inactive in resolution.
     * @param event actual synchronous hand-swap notification
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) { queue(event.getPlayer()); }
    /**
     * Queues a first settled-inventory inspection for a newly joined player.
     * @param event actual synchronous join notification
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { queue(event.getPlayer()); }
    /**
     * Defers inventory inspection until the respawn transition settles.
     * @param event actual synchronous respawn notification
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) { queue(event.getPlayer()); }
    /**
     * Cancels pending inspection and removes death-scoped bonuses immediately.
     * @param event actual synchronous death notification
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) { remove(event.getEntity().getUniqueId()); }
    /**
     * Cancels pending inspection before forgetting the disconnected UUID.
     * @param event actual synchronous quit notification
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { remove(event.getPlayer().getUniqueId()); }

    /**
     * Schedules at most one next-tick refresh per UUID, using current online/alive state.
     * Call on the server thread; this is invalidation scheduling, not shot acceptance.
     * @param player owner whose settled inventory should be inspected
     */
    public void queue(Player player) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) return;
        pending.put(id, plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(id);
            Player current = plugin.getServer().getPlayer(id);
            if (current != null && current.isOnline() && !current.isDead()) stats.refresh(current);
        }));
    }
    /**
     * Cancels queued work before clearing the owner's cached inspection and bonuses.
     * @param id dying/disconnecting owner
     */
    private void remove(UUID id) {
        var task = pending.remove(id);
        if (task != null) task.cancel();
        stats.forget(id);
    }
    /**
     * Cancels every owned refresh and clears equipment state; callers must stop event delivery first.
     */
    public void close() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        stats.clear();
    }
}
