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

/** Coalesces events until inventory mutations settle. Quit cancels callbacks before clearing state. */
public final class EquipmentListener implements Listener {
    private final Plugin plugin;
    private final EquipmentStatsService stats;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    public EquipmentListener(Plugin plugin, EquipmentStatsService stats) { this.plugin = plugin; this.stats = stats; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void inventory(PlayerInventorySlotChangeEvent event) { queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) { queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) { queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) { queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) { queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) { remove(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { remove(event.getPlayer().getUniqueId()); }

    public void queue(Player player) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) return;
        pending.put(id, plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(id);
            Player current = plugin.getServer().getPlayer(id);
            if (current != null && current.isOnline() && !current.isDead()) stats.refresh(current);
        }));
    }
    private void remove(UUID id) {
        var task = pending.remove(id);
        if (task != null) task.cancel();
        stats.forget(id);
    }
    public void close() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        stats.clear();
    }
}
