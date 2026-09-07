package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;

/**
 * Expected-failure control with an owned arrow, listener, chunk and pending callback.
 * The named exception or explicit abort must remain a failure while shared cleanup
 * assertions prove all resources were released; a successful empty run is not valid.
 */
public final class ProjectileCleanupFailureScenario implements Scenario {
    private final boolean abort;
    /**
     * Selects the deliberate exception control.
     */
    public ProjectileCleanupFailureScenario() { this(false); }
    /**
     * Chooses the exact declared cleanup failure mode.
     * @param abort use companion-abort semantics instead of the named exception
     */
    public ProjectileCleanupFailureScenario(boolean abort) { this.abort = abort; }
    /**
     * Creates live tracked resources before scheduling the deliberate failure boundary.
     * @param context server-thread report/resource owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("projectile-cleanup-v1");
        context.listen(new ImpactProbe());
        var location = Bukkit.getWorlds().getFirst().getSpawnLocation().add(0, 30, 0);
        context.tickChunk(location.getChunk());
        context.own(location.getWorld().spawn(location, Arrow.class));
        context.later(100, () -> { throw new AssertionError("Cancelled callback must never run"); });
        context.later(1, () -> {
            if (abort) context.abort();
            else throw new IllegalStateException("Deliberate projectile cleanup failure");
        });
    }
}
