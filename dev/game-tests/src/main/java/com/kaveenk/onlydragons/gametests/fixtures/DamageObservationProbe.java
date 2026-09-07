package com.kaveenk.onlydragons.gametests.fixtures;

import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

/**
 * Read-only native damage observer with event identity and post-tick health cohorts.
 * LOWEST captures incoming damage; MONITOR captures the settled event visible there.
 * One later sample groups all events for a target/dispatch tick, so shared health
 * movement is never falsely attributed to each individual hit. No damage is applied
 * and no event is synthesized by this probe.
 */
public final class DamageObservationProbe implements Listener {
    /**
     * Immutable native dispatch sample, not a production {@code DamageResult}.
     * @param sequence one-based probe event identity
     * @param tick server tick at LOWEST dispatch
     * @param target watched native target UUID
     * @param directDamager direct entity UUID, or null for non-entity causes
     * @param player direct player or projectile shooter UUID, or null
     * @param cause native damage-cause name
     * @param initialDamage raw damage before higher-priority listeners
     * @param settledDamage raw damage observed at MONITOR
     * @param finalDamage native modifier-adjusted amount at MONITOR
     * @param cancelled cancellation state at MONITOR
     * @param healthAtDispatch native HP before the event finishes
     */
    public record Damage(long sequence, int tick, UUID target, UUID directDamager, UUID player,
                         String cause, double initialDamage, double settledDamage, double finalDamage,
                         boolean cancelled, double healthAtDispatch) {}
    /**
     * One next-tick native health sample shared by a target's same-tick events.
     * @param target watched entity UUID
     * @param dispatchTick tick of the grouped event dispatches
     * @param observedTick actual later sampling tick
     * @param health native HP at that later sample
     * @param dead native dead flag at that later sample
     * @param eventSequences event IDs belonging to this cohort, defensively copied
     */
    public record HealthCohort(UUID target, int dispatchTick, int observedTick, double health,
                               boolean dead, List<Long> eventSequences) {
        /**
         * Freezes cohort membership so later event-list growth cannot rewrite attribution.
         */
        public HealthCohort { eventSequences = List.copyOf(eventSequences); }
    }
    private final ScenarioContext context;
    private final Set<UUID> targets = new HashSet<>();
    private final IdentityHashMap<EntityDamageEvent, Damage> pending = new IdentityHashMap<>();
    private final List<Damage> events = new ArrayList<>();
    private final List<HealthCohort> cohorts = new ArrayList<>();
    private final Set<String> scheduled = new HashSet<>();
    private long sequence;
    /**
     * Registers the observer through the scenario's owned listener lifecycle.
     * @param context server-thread cleanup and report owner
     */
    public DamageObservationProbe(ScenarioContext context) { this.context = context; context.listen(this); }
    /**
     * Admits a real living target without changing its HP or behavior.
     * @param target native entity whose subsequent damage events should be observed
     */
    public void watch(LivingEntity target) { targets.add(target.getUniqueId()); }
    /**
     * Returns a snapshot in native MONITOR-observation order.
     * @return immutable event list
     */
    public List<Damage> events() { return List.copyOf(events); }
    /**
     * Filters native events by the actually observed player/shooter UUID.
     * @param player source identity to select
     * @return matching event snapshot, without inferring proc ownership
     */
    public List<Damage> events(UUID player) { return events.stream().filter(row -> player.equals(row.player())).toList(); }
    /**
     * Returns completed next-tick samples; a pending cohort is not fabricated early.
     * @return immutable cohort list
     */
    public List<HealthCohort> healthCohorts() { return List.copyOf(cohorts); }
    /**
     * Captures bounded incoming events and actual direct/player source identities.
     * @param event real Paper event for a watched target
     */
    @EventHandler(priority = EventPriority.LOWEST) public void before(EntityDamageEvent event) {
        if (!targets.contains(event.getEntity().getUniqueId())) return;
        if (sequence >= 256) throw new IllegalStateException("Damage observation bound exceeded");
        Entity source = event instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
        Player player = source instanceof Player actor ? actor : source instanceof Projectile projectile
                && projectile.getShooter() instanceof Player actor ? actor : null;
        pending.put(event, new Damage(++sequence, Bukkit.getCurrentTick(), event.getEntity().getUniqueId(),
                source == null ? null : source.getUniqueId(), player == null ? null : player.getUniqueId(),
                event.getCause().name(), event.getDamage(), 0, 0, event.isCancelled(), ((LivingEntity) event.getEntity()).getHealth()));
    }
    /**
     * Pairs by event object identity and schedules one health sample per target/tick.
     * @param event the same native dispatch previously captured at LOWEST
     */
    @EventHandler(priority = EventPriority.MONITOR) public void settled(EntityDamageEvent event) {
        Damage start = pending.remove(event);
        if (start == null) return;
        Damage row = new Damage(start.sequence(), start.tick(), start.target(), start.directDamager(), start.player(),
                start.cause(), start.initialDamage(), event.getDamage(), event.getFinalDamage(), event.isCancelled(), start.healthAtDispatch());
        events.add(row);
        String cohort = row.target() + ":" + row.tick();
        if (scheduled.add(cohort)) {
            LivingEntity target = (LivingEntity) event.getEntity();
            context.later(1, () -> {
                cohorts.add(new HealthCohort(row.target(), row.tick(), Bukkit.getCurrentTick(), target.getHealth(), target.isDead(),
                        events.stream().filter(item -> item.target().equals(row.target()) && item.tick() == row.tick())
                                .map(Damage::sequence).toList()));
                publish();
            });
        }
        publish();
    }
    /**
     * Exports plain event/cohort fields while retaining null-source meaning as {@code none}.
     */
    private void publish() {
        context.observe("damageEvents", events.stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sequence", row.sequence()); out.put("tick", row.tick()); out.put("target", row.target().toString());
            out.put("directDamager", row.directDamager() == null ? "none" : row.directDamager().toString());
            out.put("player", row.player() == null ? "none" : row.player().toString()); out.put("cause", row.cause());
            out.put("initialDamage", row.initialDamage()); out.put("settledDamage", row.settledDamage());
            out.put("finalDamage", row.finalDamage()); out.put("cancelled", row.cancelled());
            out.put("healthAtDispatch", row.healthAtDispatch()); return out;
        }).toList());
        context.observe("damageHealthCohorts", cohorts.stream().map(row -> Map.of(
                "target", row.target().toString(), "dispatchTick", row.dispatchTick(), "observedTick", row.observedTick(),
                "health", row.health(), "dead", row.dead(), "eventSequences", row.eventSequences())).toList());
    }
}
