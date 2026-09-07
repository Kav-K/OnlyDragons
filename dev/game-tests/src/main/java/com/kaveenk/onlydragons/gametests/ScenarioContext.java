package com.kaveenk.onlydragons.gametests;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.gametests.fixtures.ExceptionDiagnostics;
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

/**
 * Server-thread report builder and cleanup owner for one companion scenario.
 * Assertions compare independently supplied expected/observed JSON values; setup
 * and diagnostic observations do not become acceptance assertions by themselves.
 * All resources must be registered before use so completion, failure and plugin
 * disable share the same cleanup path. It owns no external JVM, world directory
 * or shared runner lease; those remain the Python runner's responsibility.
 */
public final class ScenarioContext {
    /**
     * A deferred server-thread stage whose checked failure is reported by {@link #later}.
     * Its body may schedule the next stage; it must not block waiting for Paper ticks.
     */
    @FunctionalInterface public interface Step {
        /**
         * Performs one nonblocking scenario stage on the server thread.
         * @throws Exception when the stage must fail through the context's reporting path
         */
        void run() throws Exception;
    }
    private final GameTestsPlugin plugin;
    private final String id;
    private final long started = System.currentTimeMillis();
    private final List<Map<String, Object>> assertions = new ArrayList<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, Runnable> resources = new LinkedHashMap<>();
    private String mechanicRevision = "harness-v1";
    private boolean finished;

    /**
     * Binds the one-shot scenario to its companion boot identity.
     * @param plugin companion that publishes the immutable report text
     * @param id registered scenario identifier, independent of the mechanic revision
     */
    ScenarioContext(GameTestsPlugin plugin, String id) { this.plugin = plugin; this.id = id; }
    /**
     * Reads the runner-supplied two-boot context, if this is a restart case.
     * @return validated phase binding, or {@code null} for an ordinary boot
     * @throws Exception if the declared phase file cannot be read or is invalid
     */
    public RestartPhase restartPhase() throws Exception { return RestartPhase.load(plugin.runId()); }
    /**
     * Provides the owning companion for listener, permission and scheduler registration.
     * @return this boot's harness plugin; never the deployable production plugin
     */
    public GameTestsPlugin harness() { return plugin; }
    /**
     * Sets the mechanic identity replay must match against the scenario catalog.
     * @param revision nonempty bounded identifier, not a free-form status message
     * @throws IllegalArgumentException if the identifier is malformed
     */
    public void mechanicRevision(String revision) {
        requireActive();
        if (revision == null || !revision.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid mechanic revision");
        mechanicRevision = revision;
    }
    /**
     * Resolves the enabled production plugin by its registered name.
     * @return the real OnlyDragons instance used by the scenario
     * @throws NullPointerException if the production dependency is absent
     */
    public OnlyDragonsPlugin production() {
        return (OnlyDragonsPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("OnlyDragons"));
    }
    /**
     * Records an exact equality assertion after validating both values as report JSON.
     * Numeric wrapper types and collection equality remain significant; callers must
     * normalize intentionally rather than hiding a mismatch in the serializer.
     * @param name unique assertion ID required by the external scenario catalog
     * @param expected independently derived oracle
     * @param observed actual measured value
     * @throws IllegalArgumentException for duplicate IDs or unsupported JSON values
     */
    public void check(String name, Object expected, Object observed) {
        requireActive();
        Json.write(expected); Json.write(observed); // Reject unsupported values before completion can become terminal.
        if (assertions.stream().anyMatch(row -> row.get("id").equals(name))) throw new IllegalArgumentException("Duplicate assertion: " + name);
        assertions.add(Map.of("id", name, "expected", expected, "observed", observed, "passed", Objects.equals(expected, observed)));
    }
    /**
     * Stores JSON-compatible diagnostics without asserting their truth.
     * @param name observation key; a later sample may replace the previous value
     * @param value plain report data, not a Bukkit object or deferred supplier
     */
    public void observe(String name, Object value) { requireActive(); Json.write(value); observations.put(name, value); }
    /**
     * Registers a temporary entity for removal on every completion path.
     * @param <T> concrete entity type preserved for fluent fixture setup
     * @param entity entity spawned for this scenario
     * @return the same entity, not a clone
     */
    public <T extends Entity> T own(T entity) { requireActive(); entities.add(entity); return entity; }
    /**
     * Registers cleanup ownership before installing a native event observer.
     * @param listener scenario-local listener, removed during {@link #finish()}
     */
    public void listen(Listener listener) {
        requireActive();
        listeners.add(Objects.requireNonNull(listener));
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }
    /**
     * Registers reversible setup cleanup before the fixture mutates that resource.
     * Each callback is attempted on completion; thrown runtime failures count as
     * unreleased resources rather than preventing later resource callbacks.
     * @param name unique cleanup key within this scenario
     * @param release server-thread cleanup, including restoration of fixture permissions
     * @throws IllegalArgumentException if the key is already registered
     */
    public void cleanup(String name, Runnable release) {
        requireActive();
        if (resources.putIfAbsent(name, Objects.requireNonNull(release)) != null)
            throw new IllegalArgumentException("Duplicate cleanup resource: " + name);
    }
    /**
     * Makes a test chunk tick without players and retains only newly acquired force-loads.
     * Preexisting force-load ownership is left unchanged when this scenario ends.
     * @param chunk chunk containing fixture entities or collision geometry
     */
    public void tickChunk(Chunk chunk) {
        requireActive();
        if (!chunk.isForceLoaded()) {
            chunks.add(chunk);
            chunk.setForceLoaded(true);
        }
    }
    /**
     * Schedules a tracked one-shot stage and converts its failure into scenario failure.
     * The executing task is removed before the callback, and callbacks after completion
     * do nothing. Delays are server ticks, distinct from the runner's wall-clock timeout.
     * @param ticks scheduler delay in server ticks
     * @param action next stage, invoked on the server thread
     */
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
    /**
     * Preserves the original failure assertion, adds bounded diagnostics, then cleans up.
     * Repeated failure after completion is ignored. Diagnostic extraction cannot replace
     * the original {@code scenario_exception} or prevent the cleanup attempt.
     * @param failure actual scenario failure; never an expected-negative substitute
     */
    public void fail(Throwable failure) {
        if (finished) return;
        assertions.add(Map.of("id", "scenario_exception", "expected", "no exception", "observed", failure.toString(), "passed", false));
        try {
            observations.put("scenarioException", ExceptionDiagnostics.describe(failure));
        } catch (RuntimeException | AssertionError unavailable) {
            // Diagnostics must not prevent owned cleanup or replace the original failure assertion.
            observations.put("scenarioException", Map.of("diagnosticsUnavailable", true));
        } finally {
            finish();
        }
    }
    /**
     * Cancels owned tasks, releases setup/listeners/entities/chunks, and publishes once.
     * Cleanup assertions are added before freezing the report. The reported pass is the
     * conjunction of every assertion, including cleanup; publication errors remain
     * visible in the companion log and cannot supply the external runner a valid receipt.
     */
    public void finish() {
        if (finished) return;
        requireActive();
        int uncancelled = 0;
        for (BukkitTask task : tasks) {
            task.cancel();
            if (!task.isCancelled()) uncancelled++;
        }
        tasks.clear();
        int retainedResources = 0;
        for (Runnable release : resources.values()) {
            try { release.run(); } catch (RuntimeException failure) { retainedResources++; }
        }
        resources.clear();
        check("owned_resources_released", 0, retainedResources);
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
    /**
     * Fails an incomplete scenario through the same cleanup path used by plugin disable.
     * Already completed scenarios retain their original report.
     */
    public void abort() { if (!finished) fail(new IllegalStateException("Companion disabled before scenario completion")); }
    /**
     * Rejects cross-thread or post-completion report/resource mutations.
     * @throws IllegalStateException unless called on the primary thread before completion
     */
    private void requireActive() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Scenario state belongs to the server thread");
        if (finished) throw new IllegalStateException("Scenario is already complete");
    }
}
