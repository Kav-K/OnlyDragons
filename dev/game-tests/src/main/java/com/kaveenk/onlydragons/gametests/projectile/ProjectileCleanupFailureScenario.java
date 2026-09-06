package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;

/** Expected runner failure: cleanup must still release every owned resource. */
public final class ProjectileCleanupFailureScenario implements Scenario {
    private final boolean abort;
    public ProjectileCleanupFailureScenario() { this(false); }
    public ProjectileCleanupFailureScenario(boolean abort) { this.abort = abort; }
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
