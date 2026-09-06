package com.kaveenk.onlydragons.gametests;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

/** Owns one scenario's server-thread state, temporary entities, tasks, and assertions. */
public final class ScenarioContext {
    @FunctionalInterface public interface Step { void run() throws Exception; }
    private final GameTestsPlugin plugin;
    private final String id;
    private final long started = System.currentTimeMillis();
    private final List<Map<String, Object>> assertions = new ArrayList<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private String mechanicRevision = "harness-v1";
    private boolean finished;

    ScenarioContext(GameTestsPlugin plugin, String id) { this.plugin = plugin; this.id = id; }
    public GameTestsPlugin harness() { return plugin; }
    public void mechanicRevision(String revision) {
        requireActive();
        if (revision == null || !revision.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid mechanic revision");
        mechanicRevision = revision;
    }
    public OnlyDragonsPlugin production() {
        return (OnlyDragonsPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("OnlyDragons"));
    }
    public void check(String name, Object expected, Object observed) {
        requireActive();
        if (assertions.stream().anyMatch(row -> row.get("id").equals(name))) throw new IllegalArgumentException("Duplicate assertion: " + name);
        assertions.add(Map.of("id", name, "expected", expected, "observed", observed, "passed", Objects.equals(expected, observed)));
    }
    public void observe(String name, Object value) { requireActive(); observations.put(name, value); }
    public <T extends Entity> T own(T entity) { requireActive(); entities.add(entity); return entity; }
    /** Register before use so exception, completion and disable share cleanup. */
    public void listen(Listener listener) {
        requireActive();
        listeners.add(Objects.requireNonNull(listener));
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }
    /** Make a fresh test chunk tick without players; release only force-loads owned by this scenario. */
    public void tickChunk(Chunk chunk) {
        requireActive();
        if (!chunk.isForceLoaded()) {
            chunks.add(chunk);
            chunk.setForceLoaded(true);
        }
    }
    public void later(long ticks, Step action) {
        requireActive();
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(handle[0]); // A completed/current one-shot is no longer pending work.
            if (finished) return;
            try { action.run(); } catch (Exception | AssertionError failure) { fail(failure); }
        }, ticks);
        tasks.add(handle[0]);
    }
    public void fail(Throwable failure) {
        if (finished) return;
        assertions.add(Map.of("id", "scenario_exception", "expected", "no exception", "observed", failure.toString(), "passed", false));
        finish();
    }
    public void finish() {
        if (finished) return;
        requireActive();
        int uncancelled = 0;
        for (BukkitTask task : tasks) {
            task.cancel();
            if (!task.isCancelled()) uncancelled++;
        }
        tasks.clear();
        int retainedListeners = 0;
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
            if (HandlerList.getRegisteredListeners(plugin).stream()
                    .anyMatch(registration -> registration.getListener() == listener)) retainedListeners++;
        }
        listeners.clear();
        check("owned_listeners_removed", 0, retainedListeners);
        int retained = 0;
        for (Entity entity : entities) {
            try { entity.remove(); if (entity.isValid()) retained++; }
            catch (RuntimeException ignored) { retained++; }
        }
        entities.clear();
        int retainedChunks = 0;
        for (Chunk chunk : chunks) {
            try { chunk.setForceLoaded(false); if (chunk.isForceLoaded()) retainedChunks++; }
            catch (RuntimeException ignored) { retainedChunks++; }
        }
        chunks.clear();
        check("owned_entities_removed", 0, retained);
        check("owned_tasks_cancelled", 0, uncancelled);
        check("owned_chunk_tickets_removed", 0, retainedChunks);
        finished = true;
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("runId", plugin.runId());
        report.put("scenarioId", id);
        report.put("mechanicRevision", mechanicRevision);
        report.put("state", "complete");
        report.put("passed", assertions.stream().allMatch(row -> Boolean.TRUE.equals(row.get("passed"))));
        report.put("startedAtEpochMs", started);
        report.put("completedAtEpochMs", System.currentTimeMillis());
        report.put("server", Map.of("minecraftVersion", Bukkit.getMinecraftVersion(), "paperVersion", Bukkit.getVersion(), "bukkitVersion", Bukkit.getBukkitVersion()));
        report.put("syntheticActors", true);
        report.put("assertions", List.copyOf(assertions));
        report.put("observations", Map.copyOf(observations));
        plugin.publish(report);
    }
    /** Abort an incomplete scenario using the same path as companion shutdown. */
    public void abort() { if (!finished) fail(new IllegalStateException("Companion disabled before scenario completion")); }
    private void requireActive() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Scenario state belongs to the server thread");
        if (finished) throw new IllegalStateException("Scenario is already complete");
    }
}
