package com.kaveenk.onlydragons.paper.encounter.presentation;

import com.kaveenk.onlydragons.application.DragonHealthBar;
import com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService;
import com.kaveenk.onlydragons.paper.encounter.ManagedCombatService;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Read-only domain projection. Never infer retirement from native isValid()/isDead(). */
public final class DragonHealthPresenter implements AutoCloseable {
    private final JavaPlugin plugin;
    private final DevelopmentDragonService dragons;
    private final ManagedCombatService combat;
    private final DragonHealthBar bar = new DragonHealthBar();
    private BukkitTask task;
    private boolean closed;
    private boolean reportedFailure;
    public DragonHealthPresenter(JavaPlugin plugin, DevelopmentDragonService dragons, ManagedCombatService combat) {
        this.plugin = plugin; this.dragons = dragons; this.combat = combat;
    }
    public void start() {
        thread(); if (closed || task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 1, 1);
    }
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
    private void reportFailures() {
        if (!reportedFailure && bar.deliveryFailures() > 0) {
            reportedFailure = true; plugin.getLogger().warning("Dragon UI audience delivery failed; other viewers and cleanup continue. Inspect dragonHealth().deliveryFailures().");
        }
    }
    public long deliveryFailures() { thread(); return bar.deliveryFailures(); }
    public int viewerCount() { thread(); return bar.viewerCount(); }
    public Optional<UUID> generation() { thread(); return bar.generation(); }
    @Override public void close() {
        thread(); if (closed) return; closed = true;
        if (task != null) task.cancel(); task = null; bar.close(); reportFailures();
    }
    private static void thread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Dragon health UI requires server thread");
    }
}
