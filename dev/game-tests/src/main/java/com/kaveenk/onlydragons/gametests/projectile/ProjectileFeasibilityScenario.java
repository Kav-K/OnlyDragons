package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Cow;
import org.bukkit.util.Vector;

/** Bounded native physics experiment, using API-spawned actors without a player. */
public final class ProjectileFeasibilityScenario implements Scenario {
    private final ImpactProbe probe = new ImpactProbe();
    private final List<Map<String, Object>> trials = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private ScenarioContext context;
    private Location origin;
    private int dragonHits;

    @Override public void start(ScenarioContext context) {
        this.context = context;
        context.mechanicRevision("projectile-feasibility-v2");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.listen(probe);
        origin = Bukkit.getWorlds().getFirst().getSpawnLocation().clone().add(0, 60, 0);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            Chunk chunk = origin.getWorld().getChunkAt(origin.getChunk().getX() + x, origin.getChunk().getZ() + z);
            chunks.add(chunk);
            context.tickChunk(chunk);
        }
        awaitTicking(200);
    }
    private void awaitTicking(int remaining) {
        if (chunks.stream().allMatch(c -> c.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING)) {
            context.check("test_chunks_entity_ticking", true, true);
            cowTrial(0);
        } else if (remaining == 0) throw new IllegalStateException("Test chunks not entity ticking");
        else context.later(1, () -> awaitTicking(remaining - 1));
    }
    private Arrow launch(Location start, Vector velocity, ImpactProbe.Mode mode) {
        Arrow arrow = context.own(start.getWorld().spawn(start, Arrow.class, a -> {
            a.setGravity(false);
            a.setPersistent(false);
            a.setCritical(false);
            a.setDamage(mode == ImpactProbe.Mode.ZERO_DAMAGE ? 0 : 2);
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            a.setVelocity(velocity);
        }));
        probe.arrows.put(arrow.getUniqueId(), mode);
        return arrow;
    }
    private void cowTrial(int index) {
        ImpactProbe.Mode mode = ImpactProbe.Mode.values()[index];
        Cow cow = context.own(origin.getWorld().spawn(origin, Cow.class, c -> {
            c.setAI(false); c.setGravity(false); c.setPersistent(false);
        }));
        double health = cow.getHealth();
        Arrow arrow = launch(origin.clone().add(-6, 0.7, 0), new Vector(2, 0, 0), mode);
        var uuid = arrow.getUniqueId();
        context.later(8, () -> {
            var hits = probe.forArrow(uuid, "hit_monitor");
            var damage = probe.forArrow(uuid, "damage_monitor");
            context.check("cow_" + mode + "_collision", true, !hits.isEmpty());
            context.check("cow_" + mode + "_health", true,
                    mode == ImpactProbe.Mode.NATIVE ? cow.getHealth() < health : cow.getHealth() == health);
            if (mode == ImpactProbe.Mode.NATIVE) context.check("hit_event_precedes_damage_event", true,
                    hits.size() == 1 && damage.size() == 1
                    && ((Number) hits.getFirst().get("sequence")).intValue() < ((Number) damage.getFirst().get("sequence")).intValue());
            if (mode == ImpactProbe.Mode.CANCEL_HIT) context.check("cancel_hit_blocks_damage_event", 0, damage.size());
            if (mode == ImpactProbe.Mode.CANCEL_DAMAGE) context.check("damage_cancellation_observed", true,
                    !damage.isEmpty() && damage.stream().allMatch(e -> Boolean.TRUE.equals(e.get("cancelled"))));
            var row = new LinkedHashMap<String, Object>();
            row.put("trial", "cow_" + mode); row.put("arrow", uuid.toString());
            row.put("healthBefore", health); row.put("healthAfter", cow.getHealth());
            row.put("arrowValid", arrow.isValid()); row.put("displacement", arrow.getLocation().distance(origin.clone().add(-6, 0.7, 0)));
            trials.add(row);
            cow.remove(); arrow.remove();
            if (index + 1 < ImpactProbe.Mode.values().length) cowTrial(index + 1);
            else dragonTrial(0);
        });
    }
    private void dragonTrial(int index) {
        if (index == 9) { preSpawn(); return; }
        EnderDragon.Phase phase = index == 6 ? EnderDragon.Phase.CIRCLING
                : index == 7 ? EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET : EnderDragon.Phase.HOVER;
        EnderDragon dragon = spawnDragon(phase);
        context.later(10, () -> {
            var parts = dragon.getParts().stream().map(p -> (EnderDragonPart) p)
                    .sorted(Comparator.comparingDouble(p -> p.getBoundingBox().getWidthX())).toList();
            if (index == 0) {
                context.check("real_dragon_parts", true, parts.size() > 1);
                context.check("part_parent_mapping", true, parts.stream().allMatch(p -> p.getParent().getUniqueId().equals(dragon.getUniqueId())));
                context.observe("parts", parts.stream().map(p -> Map.of("uuid", p.getUniqueId().toString(), "name", p.getName(),
                        "width", p.getBoundingBox().getWidthX(), "height", p.getBoundingBox().getHeight())).toList());
            }
            // Geometry labels are observations, not an API guarantee of semantic head/body identity.
            EnderDragonPart aim = (index == 0 || index == 8) ? parts.getFirst() : parts.getLast();
            Vector center = aim.getBoundingBox().getCenter();
            ImpactProbe.Mode mode = index == 2 ? ImpactProbe.Mode.CANCEL_HIT
                    : index == 3 ? ImpactProbe.Mode.CANCEL_DAMAGE : index == 4 ? ImpactProbe.Mode.ZERO_DAMAGE : ImpactProbe.Mode.NATIVE;
            int count = index >= 5 && index <= 7 ? 3 : 1;
            List<Arrow> arrows = new ArrayList<>();
            Vector approach = index == 8
                    ? center.clone().subtract(dragon.getLocation().toVector()).setY(0).normalize()
                    : new Vector(0, 1, 0);
            Location arrowStart = center.toLocation(origin.getWorld()).add(approach.clone().multiply(8));
            for (int i = 0; i < count; i++) arrows.add(launch(arrowStart, approach.clone().multiply(-2), mode));
            double before = dragon.getHealth();
            int launchTick = Bukkit.getCurrentTick();
            Location dragonStart = dragon.getLocation();
            context.later(10, () -> {
                var row = new LinkedHashMap<String, Object>();
                row.put("trial", "dragon_" + index); row.put("requestedPhase", phase.name()); row.put("observedPhase", dragon.getPhase().name());
                row.put("dragon", dragon.getUniqueId().toString()); row.put("mode", mode.name());
                row.put("aimName", aim.getName()); row.put("aimPart", aim.getUniqueId().toString());
                row.put("aimWidth", aim.getBoundingBox().getWidthX()); row.put("launchTick", launchTick);
                row.put("healthBefore", before); row.put("healthAfter", dragon.getHealth());
                row.put("dragonDisplacement", dragon.getLocation().distance(dragonStart));
                row.put("arrows", arrows.stream().map(a -> a.getUniqueId().toString()).toList());
                long hits = arrows.stream().flatMap(a -> probe.forArrow(a.getUniqueId(), "hit_monitor").stream())
                        .filter(e -> e.get("parent").equals(dragon.getUniqueId().toString())).count();
                dragonHits += (int) hits;
                row.put("arrowStates", arrows.stream().map(a -> Map.of("uuid", a.getUniqueId().toString(),
                        "valid", a.isValid(), "fireTicks", a.getFireTicks(), "displacement", a.getLocation().distance(arrowStart),
                        "velocity", a.getVelocity().toString())).toList());
                var hitEvents = arrows.stream().flatMap(a -> probe.forArrow(a.getUniqueId(), "hit_monitor").stream())
                        .filter(e -> e.get("parent").equals(dragon.getUniqueId().toString())).toList();
                row.put("aimPartHits", hitEvents.stream().filter(e -> e.get("entity").equals(aim.getUniqueId().toString())).count());
                if (index == 1) context.check("body_geometry_collision", true,
                        hitEvents.size() == 1 && hitEvents.getFirst().get("entity").equals(aim.getUniqueId().toString()));
                if (index == 5) {
                    context.check("three_distinct_same_tick_impacts", true, hitEvents.size() == 3
                            && hitEvents.stream().map(e -> e.get("arrow")).distinct().count() == 3
                            && hitEvents.stream().map(e -> e.get("tick")).distinct().count() == 1);
                    context.check("dragon_hit_without_damage_event", 0L,
                            arrows.stream().flatMap(a -> probe.forArrow(a.getUniqueId(), "damage_monitor").stream()).count());
                }
                row.put("multipartHits", hits); row.put("outcome", hits == 0 ? "MISS_OR_UNSUPPORTED" : "OBSERVED_COLLISION");
                trials.add(row);
                arrows.forEach(Arrow::remove); dragon.remove();
                dragonTrial(index + 1);
            });
        });
    }
    private EnderDragon spawnDragon(EnderDragon.Phase phase) {
        return context.own(origin.getWorld().spawn(origin, EnderDragon.class, d -> {
            d.setPersistent(false); d.setPhase(phase);
        }));
    }
    private void preSpawn() {
        Location start = origin.clone().add(0, 18, 0);
        Arrow arrow = launch(start, new Vector(0, -1, 0), ImpactProbe.Mode.CANCEL_HIT);
        var uuid = arrow.getUniqueId();
        int launchTick = Bukkit.getCurrentTick();
        context.later(3, () -> {
            context.check("pre_spawn_native_flight", true, arrow.isValid() && arrow.getLocation().distance(start) > 1);
            EnderDragon dragon = spawnDragon(EnderDragon.Phase.HOVER);
            int spawnTick = Bukkit.getCurrentTick();
            context.later(24, () -> {
                context.check("pre_spawn_uuid_continuous", uuid.toString(), arrow.getUniqueId().toString());
                var hits = probe.forArrow(uuid, "hit_monitor").stream().filter(e -> e.get("parent").equals(dragon.getUniqueId().toString())).toList();
                context.observe("preSpawn", Map.of("arrow", uuid.toString(), "launchTick", launchTick, "spawnTick", spawnTick,
                        "hits", hits, "outcome", hits.isEmpty() ? "MISS_OR_UNSUPPORTED" : "OBSERVED_COLLISION"));
                context.check("pre_spawn_launch_before_spawn", true, launchTick < spawnTick);
                context.check("pre_spawn_collision_after_spawn", true, !hits.isEmpty()
                        && hits.stream().allMatch(e -> ((Number) e.get("tick")).intValue() > spawnTick));
                arrow.remove(); dragon.remove(); lifetime();
            });
        });
    }
    private void lifetime() {
        Arrow arrow = launch(origin.clone().add(0, 20, 0), new Vector(0.1, 0, 0), ImpactProbe.Mode.NATIVE);
        arrow.setLifetimeTicks(1199);
        context.check("lifetime_setter_roundtrip", 1199, arrow.getLifetimeTicks());
        var uuid = arrow.getUniqueId();
        context.later(8, () -> {
            context.observe("airborneLifetimeAfter8Ticks", arrow.getLifetimeTicks());
            context.check("airborne_lifetime_uuid_valid", true, arrow.isValid() && arrow.getUniqueId().equals(uuid));
            arrow.setLifetimeTicks(0);
            context.check("lifetime_reset_roundtrip", 0, arrow.getLifetimeTicks());
            arrow.remove();
            groundedLifetime();
        });
    }
    private void groundedLifetime() {
        var ground = origin.getWorld().getHighestBlockAt(origin.getBlockX(), origin.getBlockZ());
        Location start = ground.getLocation().add(0.5, 5, 0.5);
        Arrow expired = launch(start, new Vector(0, -1, 0), ImpactProbe.Mode.NATIVE);
        Arrow reset = launch(start.clone().add(0.2, 0, 0), new Vector(0, -1, 0), ImpactProbe.Mode.CANCEL_HIT);
        context.later(10, () -> {
            context.check("grounded_native_collision", true, expired.isInBlock());
            context.check("cancel_hit_does_not_prevent_block_collision", true, reset.isInBlock()
                    && probe.forArrow(reset.getUniqueId(), "hit_monitor").stream()
                    .anyMatch(e -> Boolean.TRUE.equals(e.get("cancelled")) && !e.get("block").equals("none")));
            expired.setLifetimeTicks(1199);
            reset.setLifetimeTicks(1199);
            reset.setLifetimeTicks(0);
            context.later(4, () -> {
                context.check("grounded_age_control_despawns", false, expired.isValid());
                context.check("grounded_age_reset_survives", true, reset.isValid());
                finish();
            });
        });
    }
    private void finish() {
        context.check("multipart_collision_observed", true, dragonHits > 0);
        context.observe("trials", List.copyOf(trials));
        context.observe("events", List.copyOf(probe.events));
        context.observe("limitations", "API-spawned arrows with no player shooter; controlled phases in disposable overworld; no client input, End landing sequence, native player damage, production adapter or persistence across restart proven.");
        context.finish();
    }
}
