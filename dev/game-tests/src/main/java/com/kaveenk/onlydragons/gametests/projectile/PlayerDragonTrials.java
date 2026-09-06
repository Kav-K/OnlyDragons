package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Public-API fixtures; arrows are never teleported or replaced. All callbacks are server-owned. */
final class PlayerDragonTrials {
    private final ScenarioContext context;
    private final ImpactProbe probe = new ImpactProbe();
    private final List<Map<String, Object>> trials = new ArrayList<>();
    private final List<Map<String, Object>> impactGeometry = new ArrayList<>();
    private Player player;
    private EnderDragon nativeDragon;
    private double nativeHealth;
    private Runnable complete;
    private boolean ownersValid = true;

    PlayerDragonTrials(ScenarioContext context) {
        this.context = context;
        context.listen(probe);
        context.listen(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void hit(org.bukkit.event.entity.ProjectileHitEvent event) {
                if (!probe.arrows.containsKey(event.getEntity().getUniqueId())
                        || !(event.getHitEntity() instanceof EnderDragonPart part)) return;
                impactGeometry.add(Map.of("arrow", event.getEntity().getUniqueId().toString(),
                        "tick", Bukkit.getCurrentTick(), "part", box(part),
                        "phase", part.getParent().getPhase().name(),
                        "arrowPosition", event.getEntity().getLocation().toVector().toString(),
                        "arrowVelocity", event.getEntity().getVelocity().toString()));
            }
        });
    }

    void prepare(Player player) {
        this.player = player;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) {
            context.tickChunk(player.getWorld().getChunkAt(x, z));
        }
        nativeDragon = dragon(EnderDragon.Phase.HOVER);
        nativeHealth = nativeDragon.getHealth();
    }

    private EnderDragon dragon(EnderDragon.Phase phase) {
        return context.own(player.getWorld().spawn(new Location(player.getWorld(), 0.5, 100, 12.5),
                EnderDragon.class, d -> { d.setPersistent(false); d.setPhase(phase); }));
    }

    void nativeRelease(Arrow arrow, Runnable complete) {
        this.complete = complete;
        probe.arrows.put(arrow.getUniqueId(), ImpactProbe.Mode.NATIVE);
        var row = begin("native_client_release", nativeDragon, List.of(arrow));
        context.later(15, () -> {
            record(row, nativeDragon, List.of(arrow), nativeHealth);
            context.check("native_release_collision", true, hits(arrow, nativeDragon).size() == 1);
            context.check("native_release_damage_positive", true, nativeDragon.getHealth() < nativeHealth
                    && !probe.forArrow(arrow.getUniqueId(), "damage_monitor").isEmpty());
            context.check("part_parent_mapping", true, nativeDragon.getParts().stream()
                    .allMatch(p -> p instanceof EnderDragonPart part && part.getParent().getUniqueId().equals(nativeDragon.getUniqueId())));
            arrow.remove(); nativeDragon.remove();
            trial(0);
        });
    }

    private void trial(int index) {
        // Four isolated controls, eight geometry attempts, then flying/seated/volley attempts.
        if (index == 15) {
            context.check("player_owned_controls", true, ownersValid && player.isOnline());
            context.observe("playerDragonTrials", List.copyOf(trials));
            context.observe("impactGeometry", List.copyOf(impactGeometry));
            context.observe("playerDragonEvents", List.copyOf(probe.events));
            context.observe("limitations", "One real client bow release; subsequent native arrows API-spawned with the same online Player as shooter. Controlled overworld phases, not natural End flight/landing. Geometry attempts are not semantic head/body passes. No production adapter, human visuals, authentication, multiplayer or performance acceptance.");
            complete.run();
            return;
        }
        EnderDragon.Phase phase = index == 12 ? EnderDragon.Phase.CIRCLING
                : index == 13 ? EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET : EnderDragon.Phase.HOVER;
        EnderDragon target = dragon(phase);
        context.later(2, () -> {
            var parts = target.getParts().stream().map(p -> (EnderDragonPart) p)
                    .sorted(Comparator.comparingDouble((EnderDragonPart p) -> p.getBoundingBox().getWidthX())
                            .thenComparingDouble(p -> p.getBoundingBox().getCenterZ())
                            .thenComparingDouble(p -> p.getBoundingBox().getCenterX())).toList();
            EnderDragonPart aim = index >= 4 && index < 12 ? parts.get((index - 4) % parts.size()) : parts.getLast();
            ImpactProbe.Mode mode = index < 4 ? ImpactProbe.Mode.values()[index] : ImpactProbe.Mode.NATIVE;
            Location start = aim.getBoundingBox().getCenter().toLocation(player.getWorld()).add(0, 0, -8);
            List<Arrow> arrows = new ArrayList<>();
            for (int n = 0; n < (index == 14 ? 3 : 1); n++) {
                Arrow arrow = context.own(player.getWorld().spawn(start, Arrow.class, a -> {
                    a.setShooter(player); a.setPersistent(false); a.setGravity(false);
                    a.setCritical(false); a.setDamage(mode == ImpactProbe.Mode.ZERO_DAMAGE ? 0 : 2);
                    a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                    a.setVelocity(new Vector(0, 0, 2));
                }));
                ownersValid &= arrow.getShooter() instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId());
                probe.arrows.put(arrow.getUniqueId(), mode);
                arrows.add(arrow);
            }
            double before = target.getHealth();
            String id = index < 4 ? "control_" + mode : "attempt_" + index;
            var row = begin(id, target, arrows);
            row.put("aimPart", aim.getUniqueId().toString());
            row.put("aimBox", box(aim));
            row.put("mode", mode.name());
            row.put("requestedPhase", phase.name());
            context.later(8, () -> {
                record(row, target, arrows, before);
                Arrow arrow = arrows.getFirst();
                var hit = hits(arrow, target);
                var damage = probe.forArrow(arrow.getUniqueId(), "damage_monitor");
                if (index < 4) {
                    context.check(id + "_collision", true, mode == ImpactProbe.Mode.CANCEL_HIT
                            ? !hit.isEmpty() && hit.stream().allMatch(e -> Boolean.TRUE.equals(e.get("cancelled")))
                            : hit.size() == 1);
                    if (mode == ImpactProbe.Mode.NATIVE) {
                        context.check(id + "_damage_positive", true, target.getHealth() < before && !damage.isEmpty());
                        context.check("native_hit_before_damage", true, hit.size() == 1 && damage.size() == 1
                                && ((Number) hit.getFirst().get("sequence")).intValue() < ((Number) damage.getFirst().get("sequence")).intValue());
                    } else {
                        context.check(id + "_suppressed", before, target.getHealth());
                        if (mode == ImpactProbe.Mode.CANCEL_HIT) context.check(id + "_no_damage_event", 0, damage.size());
                        if (mode == ImpactProbe.Mode.CANCEL_DAMAGE) context.check(id + "_cancelled_event", true,
                                damage.size() == 1 && Boolean.TRUE.equals(damage.getFirst().get("cancelled")));
                    }
                }
                if (mode == ImpactProbe.Mode.CANCEL_HIT) context.check("cancelled_arrow_crosses_multiple_parts", true,
                        hit.stream().map(e -> e.get("entity")).distinct().count() > 1);
                if (index == 4) {
                    context.check("small_geometry_actual_uuid", true, hit.size() == 1
                            && hit.getFirst().get("entity").equals(aim.getUniqueId().toString())
                            && aim.getBoundingBox().getWidthX() == 1 && aim.getBoundingBox().getHeight() == 1);
                    context.check("small_geometry_native_damage", 4.0, before - target.getHealth());
                }
                if (index == 5) {
                    context.check("large_geometry_actual_collision", true, hit.size() == 1
                            && target.getParts().stream().anyMatch(p -> p.getUniqueId().toString().equals(hit.getFirst().get("entity"))
                            && p.getBoundingBox().getWidthX() == 5 && p.getBoundingBox().getHeight() == 3));
                    context.check("large_geometry_native_damage", 2.0, before - target.getHealth());
                }
                if (index == 12 || index == 13) {
                    var geometry = impactGeometry.stream().filter(g -> g.get("arrow").equals(arrow.getUniqueId().toString())).toList();
                    context.check(index == 12 ? "flying_collision_phase" : "seated_collision_phase", true,
                            geometry.size() == 1 && geometry.getFirst().get("phase").equals(phase.name()));
                    if (index == 12) context.check("flying_dragon_moved", true,
                            !row.get("dragonStart").equals(row.get("dragonAfter")));
                    else {
                        context.check("seated_no_damage_event", 0, damage.size());
                        context.check("seated_native_health_unchanged", before, target.getHealth());
                        context.check("seated_arrow_rebound_fire", true, arrow.isValid()
                                && arrow.getFireTicks() > 0 && arrow.getVelocity().getZ() < 0);
                    }
                }
                if (index == 14) {
                    var volley = arrows.stream().flatMap(a -> hits(a, target).stream()).toList();
                    context.check("three_distinct_player_owned_same_tick_impacts", true, volley.size() == 3
                            && volley.stream().map(e -> e.get("arrow")).distinct().count() == 3
                            && volley.stream().map(e -> e.get("tick")).distinct().count() == 1);
                    context.check("volley_native_damage_event_count", 1L,
                            arrows.stream().flatMap(a -> probe.forArrow(a.getUniqueId(), "damage_monitor").stream()).count());
                    context.check("volley_native_health_loss", 2.0, before - target.getHealth());
                }
                arrows.forEach(Arrow::remove); target.remove(); trial(index + 1);
            });
        });
    }

    private List<Map<String, Object>> hits(Arrow arrow, EnderDragon dragon) {
        return probe.forArrow(arrow.getUniqueId(), "hit_monitor").stream()
                .filter(e -> e.get("parent").equals(dragon.getUniqueId().toString())).toList();
    }

    private Map<String, Object> begin(String id, EnderDragon dragon, List<Arrow> arrows) {
        var row = new LinkedHashMap<String, Object>();
        row.put("trial", id); row.put("launchTick", Bukkit.getCurrentTick());
        row.put("dragon", dragon.getUniqueId().toString());
        row.put("phaseAtLaunch", dragon.getPhase().name());
        row.put("dragonStart", dragon.getLocation().toVector().toString());
        row.put("partsAtLaunch", dragon.getParts().stream().map(p -> box((EnderDragonPart) p)).toList());
        row.put("arrowsAtLaunch", arrows.stream().map(a -> Map.of("uuid", a.getUniqueId().toString(),
                "position", a.getLocation().toVector().toString(), "velocity", a.getVelocity().toString(),
                "baseDamage", a.getDamage(), "critical", a.isCritical(), "owner", player.getUniqueId().toString())).toList());
        return row;
    }

    private Map<String, Object> box(EnderDragonPart part) {
        var b = part.getBoundingBox();
        return Map.of("uuid", part.getUniqueId().toString(), "parent", part.getParent().getUniqueId().toString(),
                "name", part.getName(), "min", List.of(b.getMinX(), b.getMinY(), b.getMinZ()),
                "max", List.of(b.getMaxX(), b.getMaxY(), b.getMaxZ()));
    }

    private void record(Map<String, Object> row, EnderDragon dragon, List<Arrow> arrows, double before) {
        var impacts = arrows.stream().flatMap(a -> hits(a, dragon).stream()).toList();
        row.put("healthBefore", before); row.put("healthAfter", dragon.getHealth());
        row.put("phaseAfter", dragon.getPhase().name());
        row.put("dragonAfter", dragon.getLocation().toVector().toString());
        row.put("partsAfter", dragon.getParts().stream().map(p -> box((EnderDragonPart) p)).toList());
        row.put("impacts", impacts);
        row.put("distinctImpactTicks", impacts.stream().map(e -> e.get("tick")).distinct().count());
        row.put("outcome", impacts.isEmpty() ? "MISS_OR_UNSUPPORTED" : "OBSERVED_COLLISION");
        row.put("arrowsAfter", arrows.stream().map(a -> Map.of("uuid", a.getUniqueId().toString(),
                "valid", a.isValid(), "position", a.getLocation().toVector().toString(),
                "velocity", a.getVelocity().toString(), "fireTicks", a.getFireTicks())).toList());
        trials.add(row);
    }
}
