package com.kaveenk.onlydragons.application;

import java.util.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;

/** One bar per retained generation; callers serialize reconciliation and close. */
public final class DragonHealthBar implements AutoCloseable {
    public record Display(UUID generation, String name, double health, double maxHealth) {
        public Display { Objects.requireNonNull(generation); Objects.requireNonNull(name); PresentationFormatter.healthProgress(health, maxHealth); }
    }
    public record Viewer(UUID id, Audience session) {
        public Viewer { Objects.requireNonNull(id); Objects.requireNonNull(session); }
    }
    private final Map<UUID, Audience> viewers = new HashMap<>();
    private UUID generation;
    private BossBar bar;
    private boolean closed;
    private long deliveryFailures;
    private Display previous;

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
    private void clear() {
        BossBar retired = bar; var oldViewers = List.copyOf(viewers.values());
        viewers.clear(); bar = null; generation = null; previous = null;
        if (retired != null) oldViewers.forEach(viewer -> hide(viewer, retired));
    }
    private void hide(Audience viewer, BossBar retired) {
        try { viewer.hideBossBar(retired); } catch (RuntimeException failure) { deliveryFailures++; }
    }
    public long deliveryFailures() { return deliveryFailures; }
    public int viewerCount() { return viewers.size(); }
    public Optional<UUID> generation() { return Optional.ofNullable(generation); }
    @Override public void close() { if (!closed) { clear(); closed = true; } }
}
