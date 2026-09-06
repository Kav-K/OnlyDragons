package com.kaveenk.onlydragons.gametests.projectile.homing;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import com.kaveenk.onlydragons.paper.projectile.homing.ArrowContinuity;
import java.util.*;
import com.kaveenk.onlydragons.paper.encounter.ManagedCombatService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Native player releases and real entity motion. Fixture setup never teleports or replaces arrows. */
public final class TracerContinuityScenario implements Scenario, Listener {
    private ScenarioContext context;
    private PlayerFixture players;
    private OwnedBowService bows;
    private World world;
    private UUID encounter, targetId;
    private EnderDragon dragon;
    private Arrow latest;
    private OwnedProjectile captured;
    private final List<SettledHit> hits = new ArrayList<>();
    private final Map<UUID, List<ArrowContinuity.Frame>> frames = new HashMap<>();
    private final Map<UUID, String> collisions = new HashMap<>();
    private final List<Map<String, Object>> journal = new ArrayList<>();
    private ManagedCombatService.Observation observation;
    private boolean finished, moveTarget;
    private int nativeReleases;
    private boolean nativeVelocityMatches = true, nativeAgeReset;
    private int nativeVelocitySamples;
    private final Set<UUID> nativeTurns = new HashSet<>();
    private final List<Map<String, Object>> nativeWitnesses = new ArrayList<>();
    @Override public void start(ScenarioContext context) throws Exception {
        this.context = context; context.mechanicRevision("tracer-continuity-v1");
        bows = context.production().bows(); players = new PlayerFixture(context);
        observation = context.production().combat().observeSettled(hits::add); context.listen(this);
        context.cleanup("tracer-service", () -> { finished = true; publishEvidence(); if (encounter != null) bows.endEncounter(encounter); observation.close(); });
        players.await("tracer actor", 300, players::allOnline, this::setup);
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }
    private void setup() {
        world = players.player("alpha").getWorld();
        var p = players.player("alpha"); p.setGameMode(GameMode.CREATIVE); p.setAllowFlight(true); p.setFlying(true); p.setInvulnerable(true);
        p.getInventory().clear(); p.getInventory().setHeldItemSlot(0);
        players.setupPosition("alpha", new Location(world, 0.5, 120, 0.5, 0, -60));
        encounter = UUID.randomUUID();
        bows.openEncounter(encounter, world, new BoundingBox(-80, 60, -80, 80, 300, 180), new MechanicRevision("tracer-fixture", "v1"));
        context.check("server_thread_and_single_task", true, Bukkit.isPrimaryThread() && bows.taskCount() == 1);
        context.check("arena_reservation_does_not_load_all_chunks", 0, bows.continuity().tickets().ticketCount());
        sample(); prefire();
    }
    private void sample() {
        if (finished) return;
        for (var owned : bows.projectiles()) bows.continuity().frame(owned.shot().projectileId()).ifPresent(frame -> {
            var list = frames.computeIfAbsent(owned.shot().projectileId(), ignored -> new ArrayList<>());
            if (list.isEmpty() || list.getLast().tick() != frame.tick()) list.add(frame);
            Arrow actual = bows.arrow(owned.shot().projectileId()).orElseThrow();
            if (frame.tick() == tick()) {
                Vector applied = actual.getVelocity();
                nativeVelocitySamples++;
                nativeVelocityMatches &= applied.distance(vec(frame.after())) < 1e-9;
                if (frame.previousLifetime() >= 1199) nativeAgeReset |= actual.getLifetimeTicks() <= 1
                        && frame.launchAge() == tick() - owned.shot().launchTick() && frame.launchAge() > 0;
                if (frame.aim().isPresent() && dragon != null) {
                    var aim = frame.aim().orElseThrow();
                    dragon.getParts().stream().filter(p -> p.getUniqueId().equals(aim.part().partId())).findFirst().ifPresent(part -> {
                        var box = part.getBoundingBox(); Vector position = actual.getLocation().toVector();
                        Vector nearest = new Vector(Math.max(box.getMinX(), Math.min(box.getMaxX(), position.getX())),
                                Math.max(box.getMinY(), Math.min(box.getMaxY(), position.getY())),
                                Math.max(box.getMinZ(), Math.min(box.getMaxZ(), position.getZ())));
                        Vector desired = nearest.clone().subtract(position); Vector before = vec(frame.before());
                        if (desired.length() > 1e-8 && before.length() > 1e-8 && applied.length() > 1e-8
                                && desired.length() <= owned.shot().enchantments().stream().filter(e -> e.id().equals("dragon_tracer")).findFirst().orElseThrow().level() * 2
                                && nearest.distance(vec(aim.point())) < 1e-9
                                && applied.clone().normalize().dot(desired.clone().normalize()) > before.clone().normalize().dot(desired.clone().normalize()) + 1e-8) {
                            nativeTurns.add(actual.getUniqueId());
                            nativeWitnesses.add(Map.of("projectile", actual.getUniqueId().toString(), "tick", tick(),
                                    "nativePosition", List.of(position.getX(), position.getY(), position.getZ()),
                                    "nativeVelocity", List.of(applied.getX(), applied.getY(), applied.getZ()),
                                    "part", part.getUniqueId().toString(), "box", box.toString(), "distance", desired.length()));
                        }
                    });
                }
            }
        });
        if (moveTarget && dragon != null && dragon.isValid()) dragon.teleport(dragon.getLocation().add(0.08, 0, 0));
        context.later(1, this::sample);
    }
    private void prefire() {
        kit(5);
        draw("prefire", 22, () -> context.later(4, () -> {
            Arrow arrow = latest; OwnedProjectile shot = captured; UUID id = arrow.getUniqueId();
            context.check("native_prefire_same_registry_uuid", true, arrow.isValid() && bows.projectile(id).orElseThrow().equals(shot));
            context.check("no_target_ballistic_velocity", true, !frames.getOrDefault(id, List.of()).isEmpty()
                    && frames.get(id).stream().allMatch(f -> f.aim().isEmpty() && f.before().equals(f.after())));
            context.check("native_gravity_and_motion", true, arrow.hasGravity() && arrow.getLocation().toVector().distance(vec(shot.shot().launchPosition())) > 5
                    && arrow.getVelocity().getY() < shot.shot().initialVelocity().y());
            arrow.setLifetimeTicks(1199); // Accelerated age boundary setup; preserve native gravity/velocity/UUID.
            long spawnTick = tick();
            spawnDragon(arrow.getLocation().add(arrow.getVelocity().multiply(3.5)));
            context.later(18, () -> {
                var ownHits = hits.stream().filter(h -> h.projectile().shot().projectileId().equals(id)).toList();
                context.check("actual_prefire_acquired_later_dragon", true, acquired(id));
                context.check("actual_prefire_physical_hit", true, ownHits.size() == 1 && ownHits.getFirst().accepted()
                        && shot.shot().launchTick() < spawnTick && spawnTick < ownHits.getFirst().collisionTick()
                        && collisions.getOrDefault(id, "").equals("DRAGON"));
                context.check("prefire_no_replacement", true, nativeReleases == 1 && ownHits.size() == 1
                        && ownHits.getFirst().projectile().equals(shot));
                context.check("age_counter_reset_on_same_entity", true, nativeAgeReset);
                removeDragon(); reset(); level(1);
            });
        }));
    }
    private void level(int level) {
        if (level == 6) { loss(); return; }
        spawnDragon(new Location(world, 0.5, 120, 20.5));
        context.later(3, () -> {
            var part = dragon.getParts().stream().filter(p -> p.getBoundingBox().getWidthX() == 5).findFirst().orElseThrow();
            Location start = part.getBoundingBox().getCenter().toLocation(world).add(0, 0.8 - players.player("alpha").getEyeHeight(), -8);
            start.setYaw(0); start.setPitch(0); players.setupPosition("alpha", start); kit(level);
            Vector targetStart = dragon.getLocation().toVector(); moveTarget = true;
            draw("level" + level, 10, () -> {
                UUID id = latest.getUniqueId();
                context.later(22, () -> {
                    context.check("level_" + level + "_native_acquisition", true, acquired(id));
                    context.check("level_" + level + "_real_target_moved", true, dragon.getLocation().toVector().distance(targetStart) > 0.5);
                    context.check("level_" + level + "_native_turn_toward_part", true, nativeTurns.contains(id));
                    var list = frames.getOrDefault(id, List.of()).stream().filter(f -> f.aim().isPresent()).toList();
                    context.check("level_" + level + "_radius_speed_turn", true, !list.isEmpty() && list.stream().allMatch(f -> validFrame(f, level * 2)));
                    context.check("level_" + level + "_captured_enchant", level, captured.shot().enchantments().stream()
                            .filter(e -> e.id().equals("dragon_tracer")).findFirst().orElseThrow().level());
                    removeDragon(); reset(); level(level + 1);
                });
            });
        });
    }
    private void loss() {
        kit(5); players.setupPosition("alpha", new Location(world, 0.5, 180, 0.5, 0, -5));
        draw("loss", 22, () -> {
            Arrow arrow = latest; UUID id = arrow.getUniqueId();
            // Move only the real target in this controlled current-position/reacquisition trial.
            spawnDragon(arrow.getLocation().add(12, -1, 6));
            follow(arrow, 4, () -> {
                context.check("target_loss_had_real_lock", true, acquired(id));
                bows.unregisterTarget(dragon.getUniqueId()); long lossTick = tick();
                follow(arrow, 3, () -> {
                    context.check("unregistered_target_ballistic", true, frames.getOrDefault(id, List.of()).stream()
                            .anyMatch(f -> f.tick() > lossTick && f.aim().isEmpty() && f.before().equals(f.after())));
                    bows.registerTarget(encounter, targetId, dragon);
                    long restored = tick(); follow(arrow, 3, () -> {
                        context.check("same_arrow_reacquires", true, arrow.isValid() && frames.getOrDefault(id, List.of()).stream()
                                .anyMatch(f -> f.tick() > restored && f.aim().isPresent()));
                        removeDragon(); reset(); wall();
                    });
                });
            });
        });
    }
    private void follow(Arrow arrow, int remaining, Runnable next) {
        if (remaining == 0) { next.run(); return; }
        if (!arrow.isValid()) throw new IllegalStateException("Follow trial arrow removed");
        dragon.teleport(arrow.getLocation().add(12, -1, 6));
        context.later(1, () -> follow(arrow, remaining - 1, next));
    }
    private void wall() {
        players.setupPosition("alpha", new Location(world, 0.5, 120, 0.5, 0, 0)); kit(5);
        var blocks = new ArrayList<org.bukkit.block.BlockState>();
        context.cleanup("wall", () -> blocks.forEach(b -> b.update(true, false)));
        for (int x = -12; x <= 12; x++) for (int y = 112; y <= 132; y++) {
            var b = world.getBlockAt(x, y, 6); blocks.add(b.getState()); b.setType(Material.STONE, false);
        }
        spawnDragon(new Location(world, 0.5, 120, 20));
        draw("wall", 10, () -> {
            Arrow arrow = latest; UUID id = arrow.getUniqueId();
            context.later(15, () -> {
                context.check("wall_prevents_acquisition", true, !acquired(id));
                context.check("actual_block_collision_retires", true, collisions.getOrDefault(id, "").equals("BLOCK")
                        && !arrow.isValid() && bows.projectile(id).isEmpty() && hits.stream().noneMatch(h -> h.projectile().shot().projectileId().equals(id)));
                removeDragon(); blocks.forEach(b -> b.update(true, false));
                spawnDragon(new Location(world, 0.5, 120, 10));
                context.later(3, () -> {
                    context.check("grounded_arrow_does_not_rearm", true, !arrow.isValid() && bows.projectile(id).isEmpty() && bows.continuity().frame(id).isEmpty());
                    removeDragon(); reset(); crossing();
                });
            });
        });
    }
    private void crossing() {
        int oldSimulation = world.getSimulationDistance(), oldView = world.getViewDistance();
        context.cleanup("world-distances", () -> { world.setSimulationDistance(oldSimulation); world.setViewDistance(oldView); });
        world.setSimulationDistance(2); world.setViewDistance(2);
        bows.endEncounter(encounter); encounter = UUID.randomUUID();
        bows.openEncounter(encounter, world, new BoundingBox(1000, 60, 1000, 1100, 300, 1250), new MechanicRevision("tracer-fixture", "v1"));
        players.setupPosition("alpha", new Location(world, 1024.5, 220, 1024.5, 0, -10)); kit(5);
        // Distant from spawn and six chunks ahead of the only player. The false overload observes without loading FULL.
        var destination = world.getChunkAt(64, 70, false);
        players.await("destination outside player simulation", 100, () -> destination.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING, () -> {
            context.check("distant_destination_has_no_ticking_influence", true, !destination.isForceLoaded()
                    && world.getPluginChunkTickets(64, 70).isEmpty() && world.getSimulationDistance() == 2
                    && world.getPlayers().stream().allMatch(p -> p.getLocation().distanceSquared(new Location(world, 1032, 220, 1128)) > 80 * 80));
            context.observe("destinationBeforeDemand", Map.of("x", 64, "z", 70, "loadLevel", destination.getLoadLevel().name(),
                    "simulationDistance", world.getSimulationDistance(), "forceLoaded", destination.isForceLoaded()));
            draw("crossing", 22, () -> {
                Arrow arrow = latest; var shot = captured; UUID id = arrow.getUniqueId();
                int firstChunk = ((int) Math.floor(shot.shot().launchPosition().z())) >> 4;
                UUID otherDemand = UUID.randomUUID();
                bows.continuity().tickets().retain(otherDemand, encounter, arrow.getLocation().getBlockX() >> 4, arrow.getLocation().getBlockZ() >> 4);
                players.request("alpha", "end");
                players.await("actual quit with retained arrow", 100, () -> world.getPlayers().isEmpty(), () ->
                    players.await("native arrow reaches distant ticketed destination", 120,
                        () -> arrow.isValid() && (arrow.getLocation().getBlockZ() >> 4) >= 70, () -> {
                            Vector arrival = arrow.getLocation().toVector();
                            context.check("destination_promoted_by_broker", true, world.getPlayers().isEmpty()
                                    && destination.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING
                                    && world.getPluginChunkTickets(64, 70).contains(context.production()) && !destination.isForceLoaded());
                            context.later(3, () -> {
                                context.check("quit_retains_captured_arrow_and_mechanics", true, arrow.isValid() && bows.projectile(id).orElseThrow().equals(shot)
                                        && !bows.isCurrentSession(shot.shot().ownerId(), shot.sessionToken()));
                                context.check("physical_chunk_boundary_same_uuid", true, (arrow.getLocation().getBlockZ() >> 4) != firstChunk
                                        && arrow.getLocation().toVector().distance(arrival) > 1 && world.getPlayers().isEmpty()
                                        && arrow.getChunk().getPluginChunkTickets().contains(context.production()));
                                context.check("continuity_without_target_no_speed_boost", true, frames.getOrDefault(id, List.of()).stream().allMatch(f -> f.before().equals(f.after())));
                                context.observe("destinationNativeProgress", Map.of("projectile", id.toString(), "arrival", arrival.toString(),
                                        "after", arrow.getLocation().toVector().toString(), "loadLevel", destination.getLoadLevel().name(), "players", world.getPlayers().size()));
                                arrow.remove(); context.later(2, () -> {
                                    context.check("unexpected_removal_recorded_no_replacement", true, bows.projectile(id).isEmpty()
                                            && bows.trace().stream().anyMatch(t -> t.projectileId().equals(id) && t.kind().equals("retired") && t.detail().equals("REMOVED")));
                                    context.check("other_ticket_demand_survives_arrow_retirement", true, bows.continuity().tickets().demandCount() == 1 && bows.continuity().tickets().ticketCount() == 9);
                                    bows.continuity().tickets().release(otherDemand);
                                    context.check("last_demand_releases_tickets", 0, bows.continuity().tickets().ticketCount());
                                    context.check("native_destination_ticket_released", false, world.getPluginChunkTickets(64, 70).contains(context.production()));
                                    finish();
                                });
                            });
                        }));
            });
        });
    }
    private void finish() {
        finished = true; bows.endEncounter(encounter);
        context.check("native_velocity_matches_applied_steering", true, nativeVelocitySamples > 10 && nativeVelocityMatches);
        context.check("reset_releases_registry_frames_reservations", true, bows.capacityUsed() == 0 && bows.continuity().frameCount() == 0
                && bows.continuity().tickets().reservedCount() == 0 && bows.continuity().tickets().demandCount() == 0);
        observation.close(); bows.close();
        context.check("disable_releases_task_tickets", true, bows.taskCount() == 0 && bows.continuity().tickets().ticketCount() == 0);
        // Cleanup must remain safe after explicit terminal service close.
        encounter = null;
        publishEvidence();
        context.finish();
    }
    private void publishEvidence() {
        context.observe("nativeFlight", journal);
        context.observe("nativeSteeringWitnesses", nativeWitnesses);
        context.observe("frameSamples", frames.entrySet().stream().map(e -> Map.of("projectile", e.getKey().toString(), "frames",
                e.getValue().stream().map(f -> Map.of("tick", f.tick(), "age", f.launchAge(), "position", list(f.position()),
                        "before", list(f.before()), "after", list(f.after()), "distance", f.aim().map(a -> a.distance()).orElse(-1.0),
                        "target", f.aim().map(a -> a.part().targetId().toString()).orElse("none"))).toList())).toList());
    }
    private void reset() { bows.endEncounter(encounter); encounter = UUID.randomUUID(); bows.openEncounter(encounter, world,
            new BoundingBox(-80, 60, -80, 80, 300, 180), new MechanicRevision("tracer-fixture", "v1")); }
    private void spawnDragon(Location at) {
        dragon = context.own(world.spawn(at, EnderDragon.class, d -> { d.setPersistent(false); d.setPhase(EnderDragon.Phase.HOVER); }));
        targetId = UUID.randomUUID(); bows.registerTarget(encounter, targetId, dragon);
    }
    private void removeDragon() { moveTarget = false; if (dragon != null) { bows.unregisterTarget(dragon.getUniqueId()); dragon.remove(); dragon = null; } }
    private void kit(int level) {
        var registry = CalibrationLoadouts.registry(); var item = registry.edit(registry.create("tracer"), Map.of("dragon_tracer", level), List.of());
        players.setupItem("alpha", 0, new WeaponItemCodec(registry).encode(item));
        players.setupItem("alpha", 9, new ItemStack(Material.ARROW, 64)); players.player("alpha").updateInventory();
    }
    private void draw(String prefix, int duration, Runnable next) {
        int before = nativeReleases; players.request("alpha", prefix + "-use");
        players.await("draw " + prefix, 60, () -> players.player("alpha").isHandRaised(), () -> context.later(duration, () -> {
            players.request("alpha", prefix + "-release");
            players.await("release " + prefix, 60, () -> nativeReleases > before, () -> context.later(1, next::run));
        }));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void release(EntityShootBowEvent e) {
        if (!e.getEntity().getUniqueId().equals(players.identity("alpha")) || e.isCancelled()) return;
        latest = (Arrow) e.getProjectile(); captured = bows.projectile(latest.getUniqueId()).orElseThrow(); nativeReleases++;
        journal.add(Map.of("kind", "native-release", "projectile", latest.getUniqueId().toString(), "tick", tick(), "position", list(captured.shot().launchPosition())));
    }
    @EventHandler(priority=EventPriority.MONITOR) public void collision(ProjectileHitEvent e) {
        String kind = e.getHitEntity() instanceof EnderDragonPart ? "DRAGON" : e.getHitBlock() != null ? "BLOCK" : "OTHER";
        collisions.putIfAbsent(e.getEntity().getUniqueId(), kind);
        journal.add(Map.of("kind", "physical-hit", "projectile", e.getEntity().getUniqueId().toString(), "tick", tick(), "collision", kind));
    }
    private boolean acquired(UUID id) { return frames.getOrDefault(id, List.of()).stream().anyMatch(f -> f.aim().isPresent()); }
    private boolean validFrame(ArrowContinuity.Frame f, double radius) {
        Vector a = vec(f.before()), b = vec(f.after()); double speed = a.length();
        return f.aim().orElseThrow().distance() <= radius && Math.abs(speed-b.length()) < 1e-9
                && (speed == 0 || Math.acos(Math.max(-1, Math.min(1, a.dot(b)/(speed*b.length())))) <= Math.toRadians(6) + 1e-9);
    }
    private static Vector vec(Vector3 v) { return new Vector(v.x(), v.y(), v.z()); }
    private static List<Double> list(Vector3 v) { return List.of(v.x(), v.y(), v.z()); }
    private static long tick() { return Integer.toUnsignedLong(Bukkit.getCurrentTick()); }
}
