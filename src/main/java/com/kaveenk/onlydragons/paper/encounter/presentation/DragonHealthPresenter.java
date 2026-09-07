package com.kaveenk.onlydragons.paper.encounter.presentation;

import com.kaveenk.onlydragons.application.DragonHealthBar;
import com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService;
import com.kaveenk.onlydragons.paper.encounter.ManagedCombatService;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Read-only domain-health projection using one plugin-owned synchronous loop.
 * Retained combat ownership, not native isValid/isDead, controls bar retirement.
 * Viewers are all current players in the configured world, with exact Audience
 * sessions reconciled by {@link DragonHealthBar}; no combat membership is granted.
 */
public final class DragonHealthPresenter implements AutoCloseable {
    private final JavaPlugin plugin;
    private final DevelopmentDragonService dragons;
    private final ManagedCombatService combat;
    private final DragonHealthBar bar = new DragonHealthBar();
    private BukkitTask task;
    private boolean closed;
    private boolean reportedFailure;
    /**
     * Binds the existing development/combat owners without starting a scheduler task.
     * @param plugin owner of the synchronous presentation loop
     * @param dragons current generation, arena and captured display selection
     * @param combat retained native-ownership authority
     */
    public DragonHealthPresenter(JavaPlugin plugin, DevelopmentDragonService dragons, ManagedCombatService combat) {
        this.plugin = plugin; this.dragons = dragons; this.combat = combat;
    }
    /**
     * Starts one next-tick/every-tick reconciler; repeated calls and closed instances do nothing.
     * @throws IllegalStateException if called off the server thread
     */
    public void start() {
        thread(); if (closed || task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 1, 1);
    }
    /**
     * Projects current domain HP or removes stale bars, then reconciles current world viewers; no native HP is read as accounting.
     */
    private void reconcile() {
        thread();
        var view = dragons.view();
        var arena = dragons.arena();
        if (view.isEmpty() || arena.isEmpty() || !combat.ownsEntity(view.get().entityId())) {
            bar.reconcile(Optional.empty(), List.of()); reportFailures(); return;
        }
        var target = view.get().target();
        var display = new DragonHealthBar.Display(view.get().encounterId(), dragons.selection().orElseThrow().displayName(),
                target.currentHealth(), target.maxHealth());
        var viewers = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld().getKey().toString().equals(arena.get().worldKey()))
                .map(player -> new DragonHealthBar.Viewer(player.getUniqueId(), player)).toList();
        bar.reconcile(Optional.of(display), viewers); reportFailures();
    }
    /**
     * Warns once when isolated audience delivery failures first become observable; later viewers and cleanup continue.
     */
    private void reportFailures() {
        if (!reportedFailure && bar.deliveryFailures() > 0) {
            reportedFailure = true; plugin.getLogger().warning("Dragon UI audience delivery failed; other viewers and cleanup continue. Inspect dragonHealth().deliveryFailures().");
        }
    }
    /**
     * Reads the bar's cumulative audience failure count.
     * @return isolated failed show/update/hide deliveries
     * @throws IllegalStateException if called off the server thread
     */
    public long deliveryFailures() { thread(); return bar.deliveryFailures(); }
    /**
     * Reports tracked audience sessions, not combat participants.
     * @return currently reconciled viewer count
     * @throws IllegalStateException if called off the server thread
     */
    public int viewerCount() { thread(); return bar.viewerCount(); }
    /**
     * Reads the generation currently displayed by the bar.
     * @return empty when no retained native ownership is displayed
     * @throws IllegalStateException if called off the server thread
     */
    public Optional<UUID> generation() { thread(); return bar.generation(); }
    /**
     * Cancels its loop and hides all owned bars once, before combat/dragon shutdown.
     * @throws IllegalStateException if called off the server thread
     */
    @Override public void close() {
        thread(); if (closed) return; closed = true;
        if (task != null) task.cancel(); task = null; bar.close(); reportFailures();
    }
    /**
     * Rejects off-thread Bukkit/Audience access; this adapter does not claim Folia support.
     */
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Dragon health UI requires server thread");
    }
}
