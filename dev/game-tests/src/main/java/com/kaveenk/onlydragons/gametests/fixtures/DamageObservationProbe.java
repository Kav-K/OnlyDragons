package com.kaveenk.onlydragons.gametests.fixtures;

import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

/** Read-only observations of real Paper dispatch. One post-tick health cohort per target, never per-hit attribution. */
public final class DamageObservationProbe implements Listener {
    public record Damage(long sequence, int tick, UUID target, UUID directDamager, UUID player,
                         String cause, double initialDamage, double settledDamage, double finalDamage,
                         boolean cancelled, double healthAtDispatch) {}
    public record HealthCohort(UUID target, int dispatchTick, int observedTick, double health,
                               boolean dead, List<Long> eventSequences) {
        public HealthCohort { eventSequences = List.copyOf(eventSequences); }
    }
    private final ScenarioContext context;
    private final Set<UUID> targets = new HashSet<>();
    private final IdentityHashMap<EntityDamageEvent, Damage> pending = new IdentityHashMap<>();
    private final List<Damage> events = new ArrayList<>();
    private final List<HealthCohort> cohorts = new ArrayList<>();
    private final Set<String> scheduled = new HashSet<>();
    private long sequence;
    public DamageObservationProbe(ScenarioContext context) { this.context = context; context.listen(this); }
    public void watch(LivingEntity target) { targets.add(target.getUniqueId()); }
    public List<Damage> events() { return List.copyOf(events); }
    public List<Damage> events(UUID player) { return events.stream().filter(row -> player.equals(row.player())).toList(); }
    public List<HealthCohort> healthCohorts() { return List.copyOf(cohorts); }
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
