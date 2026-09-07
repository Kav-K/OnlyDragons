package com.kaveenk.onlydragons.application;

import java.util.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;

/**
 * One bar per retained generation; callers serialize reconciliation and close.
 * <p>
 * Mutable Adventure presentation state with no scheduler and no internal thread check/locking.
 * The Paper presenter calls it serially on the server thread, supplies only currently eligible
 * world viewers, and closes it before encounter teardown. Zero HP retains the same bar until
 * the caller supplies no display; native retirement is not inferred from health.
 * @see PresentationFormatter
 */
public final class DragonHealthBar implements AutoCloseable {
    /**
     * Immutable requested presentation, independent of contribution score.
     * @param generation nonnull encounter UUID that determines bar identity
     * @param name nonnull literal title; blank is allowed
     * @param health finite domain HP for display, clamped only when calculating progress
     * @param maxHealth finite strictly positive maximum HP
     */
    public record Display(UUID generation, String name, double health, double maxHealth) {
        /**
         * Validates identity/text presence and display-health arithmetic, not native entity liveness.
         */
        public Display { Objects.requireNonNull(generation); Objects.requireNonNull(name); PresentationFormatter.healthProgress(health, maxHealth); }
    }
    /**
     * Current viewer identity and exact Audience session; reconnect replaces the session object.
     * @param id nonnull player UUID
     * @param session nonnull live delivery target, compared by object identity during reconciliation
     */
    public record Viewer(UUID id, Audience session) {
        /**
         * Rejects null identity or audience; caller owns eligibility and session lifetime.
         */
        public Viewer { Objects.requireNonNull(id); Objects.requireNonNull(session); }
    }
    /**
     * Successfully shown audiences by UUID; exact object identity distinguishes reconnects.
     */
    private final Map<UUID, Audience> viewers = new HashMap<>();
    /**
     * Retained display generation, null when cleared.
     */
    private UUID generation;
    /**
     * Single retained Adventure bar, null when cleared; caller owns serial access.
     */
    private BossBar bar;
    /**
     * Terminal flag making later reconciliation inert.
     */
    private boolean closed;
    /**
     * Cumulative swallowed show/hide RuntimeExceptions, including failed cleanup deliveries.
     */
    private long deliveryFailures;
    /**
     * Last display used to update the bar, null after clear; suppresses redundant title/progress updates.
     */
    private Display previous;

    /**
     * Creates one red progress bar per generation, updates changed display values and reconciles
     * viewers by UUID plus exact session object. Duplicate UUID inputs use the last supplied session.
     * Show/hide RuntimeExceptions increment a counter without skipping other viewers; failed shows
     * are retried by later reconciliation, while failed hides are forgotten locally. No reentrant
     * callbacks are supported; callers must serialize this operation and close.
     * After close this method returns without inspecting inputs.
     * @param display nonnull optional current display; empty clears retained generation/viewers
     * @param eligible nonnull collection of current viewers when display is present
     */
    public void reconcile(Optional<Display> display, Collection<Viewer> eligible) {
        if (closed) return;
        if (display.isEmpty()) { clear(); return; }
        var current = display.get();
        if (!current.generation().equals(generation)) {
            clear();
            generation = current.generation();
            bar = BossBar.bossBar(PresentationFormatter.healthTitle(current.name(), current.health(), current.maxHealth()),
                    PresentationFormatter.healthProgress(current.health(), current.maxHealth()), BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        } else if (!current.equals(previous)) {
            bar.name(PresentationFormatter.healthTitle(current.name(), current.health(), current.maxHealth()));
            bar.progress(PresentationFormatter.healthProgress(current.health(), current.maxHealth()));
        }
        previous = current;
        var wanted = new HashMap<UUID, Audience>();
        eligible.forEach(viewer -> wanted.put(viewer.id(), viewer.session()));
        for (var entry : List.copyOf(viewers.entrySet())) {
            if (wanted.get(entry.getKey()) != entry.getValue()) {
                hide(entry.getValue(), bar); viewers.remove(entry.getKey());
            }
        }
        for (var entry : wanted.entrySet()) {
            if (!viewers.containsKey(entry.getKey())) {
                try { entry.getValue().showBossBar(bar); viewers.put(entry.getKey(), entry.getValue()); }
                catch (RuntimeException failure) { deliveryFailures++; }
            }
        }
    }
    /**
     * Detaches all local state before attempting each old viewer removal; one failed delivery
     * cannot retain the generation or prevent another viewer cleanup.
     */
    private void clear() {
        BossBar retired = bar; var oldViewers = List.copyOf(viewers.values());
        viewers.clear(); bar = null; generation = null; previous = null;
        if (retired != null) oldViewers.forEach(viewer -> hide(viewer, retired));
    }
    /**
     * Attempts one Audience hide and records a RuntimeException as a delivery failure without rethrowing.
     */
    private void hide(Audience viewer, BossBar retired) {
        try { viewer.hideBossBar(retired); } catch (RuntimeException failure) { deliveryFailures++; }
    }
    /**
     * Returns cumulative failed show/hide attempts across generations, including cleanup failures.
     */
    public long deliveryFailures() { return deliveryFailures; }
    /**
     * Returns locally tracked successful viewers; this is not confirmation of client packet receipt.
     */
    public int viewerCount() { return viewers.size(); }
    /**
     * Returns the currently retained generation, or empty after clear/close; inspection does not reconcile.
     */
    public Optional<UUID> generation() { return Optional.ofNullable(generation); }
    /**
     * Idempotently attempts removal for every retained viewer and marks the bar terminal.
     * Later reconciliation is inert; delivery failures remain observable.
     */
    @Override public void close() { if (!closed) { clear(); closed = true; } }
}
