package com.kaveenk.onlydragons.gametests.projectile.homing;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.projectile.homing.ArrowContinuity;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Actual native releases and current-part observations; server positioning/walls are explicit fixture setup. */
public final class AimedTracerScenario implements Scenario, Listener {
    private ScenarioContext c;
    private PlayerFixture players;
    private DevelopmentDragonService dragons;
    private EnderDragon dragon;
    private Arrow latest;
    private OwnedProjectile captured;
    private boolean finished, obstructOnLock;
    private String stage;
    private final List<SettledHit> hits = new ArrayList<>();
    private final Map<UUID, String> collisions = new LinkedHashMap<>();
    private final Map<UUID, List<ArrowContinuity.Frame>> paths = new LinkedHashMap<>();
    private final Map<org.bukkit.block.Block, org.bukkit.block.data.BlockData> wall = new LinkedHashMap<>();
    private int appliedSamples, realPartSamples;
    private final Map<UUID, List<Double>> sampledDistances = new LinkedHashMap<>();
    private boolean appliedCorrect = true;
    private Location dragonAtRelease;
    private long turnTick;

    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("aimed-tracer-v3"); players = new PlayerFixture(c);
        dragons = c.production().dragons(); c.listen(this);
        var observer = c.production().combat().observeSettled(hits::add);
        c.cleanup("aimed-tracer", () -> {
            finished = true; observer.close(); restoreWall();
            if (dragons.generation().isPresent()) dragons.reset(dragons.generation().orElseThrow());
        });
        players.await("aimed tracer actor", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    private Player player() { return players.player("alpha"); }
    private void setup() throws Exception {
        player().setGameMode(GameMode.CREATIVE); player().setAllowFlight(true); player().setFlying(true);
        player().setInvulnerable(true); player().getInventory().clear(); player().getInventory().setHeldItemSlot(0);
        players.permission("alpha", "onlydragons.fire", true);
        dragons.setup(new DevelopmentArena(player().getWorld().getKey().toString(), 160, 100, 160, 48, "test_dragon"));
        sample(); trial("aimed", 5, () -> shoot(this::aimed));
    }
    private void trial(String name, int level, ScenarioContext.Step next) {
        restoreWall();
        if (dragons.generation().isPresent()) dragons.reset(dragons.generation().orElseThrow());
        c.check(name + "_previous_owned_cleanup", true, c.production().bows().capacityUsed() == 0
                && c.production().bows().continuity().frameCount() == 0
                && c.production().bows().continuity().tickets().demandCount() == 0);
        stage = name; latest = null; captured = null; obstructOnLock = false;
        dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);
        dragon = (EnderDragon) Bukkit.getEntity(dragons.view().orElseThrow().entityId());
        var registry = CalibrationLoadouts.fireRegistry();
        var item = registry.edit(registry.create(name.equals("aimed") ? "ordinary_v4" : "drawn_training_v4"), level == 0 ? Map.of() : Map.of("dragon_tracer", level), List.of());
        players.setupItem("alpha", 0, new WeaponItemCodec(registry).encode(item));
        players.setupItem("alpha", 9, new ItemStack(Material.ARROW, 64));
        players.setupPosition("alpha", new Location(player().getWorld(), 160, 100-player().getEyeHeight(), 128, 0, -24));
        c.later(180, next);
    }
    private void shoot(ScenarioContext.Step next) {
        latest = null; players.request("alpha", stage + "-use");
        players.await("native draw " + stage, 80, player()::isHandRaised, () -> c.later(25, () -> {
            // Fix the fixture's launch pose immediately before the actual client release.
            // No arrow is moved or redirected by setup after it has been fired.
            var body = dragon.getParts().stream().filter(p -> p.getBoundingBox().getWidthX() == 5).findFirst().orElseThrow();
            Vector toBody = body.getBoundingBox().getCenter().subtract(player().getEyeLocation().toVector());
            Location aimed = player().getLocation().setDirection(toBody);
            if (stage.equals("side") || stage.equals("behind")) {
                // Stay close enough that a radius-only V shot would qualify.
                var center = body.getBoundingBox().getCenter();
                aimed = new Location(player().getWorld(), center.getX(), center.getY()-player().getEyeHeight(), center.getZ()-12,
                        stage.equals("side") ? 90 : 180, 0);
                players.setupPosition("alpha", aimed);
            } else player().setRotation(aimed.getYaw(), aimed.getPitch()-24);
            c.later(3, () -> {
                players.request("alpha", stage + "-release");
                players.await("native release " + stage, 80, () -> latest != null, () -> {
                    captured = c.production().bows().projectile(latest.getUniqueId()).orElseThrow();
                    dragonAtRelease = dragon.getLocation();
                    c.check(stage + "_captured_native_shot", true, captured.tracerProfile() == TracerProfile.AIMED_V3
                            && captured.shot().drawScale() == 1 && latest.hasGravity()
                            && captured.shot().weapon().definitionId().equals(stage.equals("aimed") ? "ordinary_v4" : "drawn_training_v4")
                            && captured.shot().projectileId().equals(latest.getUniqueId())
                            && latest.getShooter() == player());
                    next.run();
                });
            });
        }));
    }
    private void aimed() {
        // Turning away after launch must not remove an already aimed shot's eligibility either.
        players.request("alpha", "aimed-turn");
        players.await("post launch turn away", 60, () -> Math.abs(Math.abs(player().getYaw())-180) < .01, () -> { observeTurn(); c.later(45, () -> {
            var path = path(); var impact = hit();
            c.check("aimed_moving_part_assistance_and_collision", true, impact.isPresent() && impact.get().accepted()
                    && "DRAGON".equals(collisions.get(captured.shot().projectileId()))
                    && path.stream().anyMatch(f -> f.aim().isPresent() && !f.before().equals(f.after()))
                    && dragon.getLocation().distance(dragonAtRelease) > 2);
            c.check("aimed_exact_health_credit", true, health() == 900 && credit() == 100
                    && dragons.view().orElseThrow().acceptedImpacts() == 1 && dragon.getHealth() == 180);
            c.check("aimed_native_speed_turn_grace_cone", true, path.size() > 3 && path.stream().allMatch(this::validFrame)
                    && path.stream().anyMatch(f -> f.groupLaunchAge() < 3 && f.aim().isEmpty()));
            c.check("aimed_postshot_turn_keeps_capture", true, captured.shot().initialVelocity().z() > 0
                    && Math.abs(Math.abs(player().getYaw())-180) < .01 && impact.isPresent()
                    && impact.get().projectile().equals(captured)
                    && path.stream().anyMatch(f -> f.tick() >= turnTick && f.aim().isPresent()));
            journal(); trial("plain", 0, () -> shoot(() -> miss(() -> trial("side", 5, () -> shoot(this::turnedMiss)))));
        }); });
    }
    private void turnedMiss() {
        players.request("alpha", stage + "-turn");
        players.await("post launch turn toward target", 60, () -> Math.abs(player().getYaw()) < .01, () -> { observeTurn(); miss(() -> {
            c.check(stage + "_turn_cannot_authorize", true, path().stream().allMatch(f -> f.aim().isEmpty())
                    && (stage.equals("side") ? captured.shot().initialVelocity().x() < -2 : captured.shot().initialVelocity().z() < -2)
                    && Math.abs(player().getYaw()) < .01 && path().stream().anyMatch(f -> f.tick() >= turnTick));
            c.check(stage + "_inside_v_radius_still_misses", true, sampledDistances.getOrDefault(captured.shot().projectileId(), List.of()).stream().anyMatch(d -> d <= 20));
            if (stage.equals("side")) trial("behind", 5, () -> shoot(this::turnedMiss));
            else trial("range", 1, () -> shoot(() -> miss(this::obstruction)));
        }); });
    }
    private void observeTurn() {
        turnTick = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        c.check(stage + "_turn_while_airborne", true, latest.isValid()
                && c.production().bows().projectile(captured.shot().projectileId()).isPresent());
        c.observe(stage + "Turn", Map.of("tick", turnTick, "yaw", player().getYaw(), "pitch", player().getPitch()));
    }
    private void miss(ScenarioContext.Step next) {
        c.later(50, () -> {
            c.check(stage + "_ballistic_miss_no_damage", true, !path().isEmpty() && hit().isEmpty()
                    && path().stream().allMatch(f -> f.aim().isEmpty() && f.before().equals(f.after()))
                    && health() == 1000 && credit() == 0 && c.production().bows().projectile(captured.shot().projectileId()).isEmpty());
            if (stage.equals("range")) c.check("range_never_locks_outside_current_radius", true,
                    path().stream().anyMatch(f -> f.groupLaunchAge() >= 3)
                    && sampledDistances.getOrDefault(captured.shot().projectileId(), List.of()).stream().allMatch(d -> d > 4));
            journal(); next.run();
        });
    }
    private void obstruction() {
        trial("blocked", 5, () -> shoot(() -> {
            obstructOnLock = true;
            c.later(50, () -> {
                var path = path(); int first = -1;
                for (int i=0; i<path.size(); i++) if (path.get(i).aim().isPresent()) { first=i; break; }
                boolean lost = first >= 0 && path.subList(first+1, path.size()).stream().anyMatch(f -> f.aim().isEmpty() && f.before().equals(f.after()));
                c.check("obstruction_releases_existing_lock_and_hits_block", true, !wall.isEmpty() && lost
                        && "BLOCK".equals(collisions.get(captured.shot().projectileId())) && hit().isEmpty() && health() == 1000 && credit() == 0);
                c.check("observed_native_velocity_and_real_part_aim", true, appliedSamples > 10 && realPartSamples > 0 && appliedCorrect);
                journal(); UUID retired = dragon.getUniqueId(); dragons.reset(dragons.generation().orElseThrow());
                c.check("aimed_reset_owned_cleanup", true, Bukkit.getEntity(retired) == null && c.production().bows().capacityUsed() == 0
                        && c.production().bows().pendingGroups() == 0 && c.production().bows().continuity().frameCount() == 0
                        && c.production().bows().continuity().tickets().demandCount() == 0
                        && c.production().bows().continuity().tickets().reservedCount() == 0);
                c.observe("playerActions", players.journal()); finished = true; players.request("alpha", "end");
                players.await("actual quit", 100, () -> players.quits("alpha") == 1, c::finish);
            });
        }));
    }
    private void sample() {
        if (finished) return;
        for (var owned : c.production().bows().projectiles()) c.production().bows().continuity().frame(owned.shot().projectileId()).ifPresent(f -> {
            var path = paths.computeIfAbsent(owned.shot().projectileId(), ignored -> new ArrayList<>());
            if (path.isEmpty() || path.getLast().tick() != f.tick()) path.add(f);
            if (f.tick() == Integer.toUnsignedLong(Bukkit.getCurrentTick())) {
                var arrow = c.production().bows().arrow(owned.shot().projectileId()).orElseThrow();
                if (f.groupLaunchAge() >= 3) sampledDistances.computeIfAbsent(owned.shot().projectileId(), ignored -> new ArrayList<>()).add(distanceToParts(f.position()));
                appliedSamples++; appliedCorrect &= arrow.getVelocity().distance(vector(f.after())) < 1e-9;
                f.aim().ifPresent(a -> {
                    if (dragon.getParts().stream().anyMatch(p -> p.getUniqueId().equals(a.part().partId())
                            && p.getBoundingBox().getMin().distance(vector(a.part().box().min())) < 1e-9
                            && p.getBoundingBox().getMax().distance(vector(a.part().box().max())) < 1e-9)) realPartSamples++;
                    if (obstructOnLock && wall.isEmpty()) installWall(f);
                });
            }
        });
        c.later(1, this::sample);
    }
    private void installWall(ArrowContinuity.Frame frame) {
        int z = (int)Math.ceil(frame.position().z()+5);
        for (int x=112; x<=208; x++) for (int y=52; y<=148; y++) {
            var block = player().getWorld().getBlockAt(x,y,z); wall.put(block,block.getBlockData()); block.setType(Material.STONE,false);
        }
    }
    private void restoreWall() { wall.forEach((b,data) -> b.setBlockData(data,false)); wall.clear(); }
    private List<ArrowContinuity.Frame> path() { return paths.getOrDefault(captured.shot().projectileId(), List.of()); }
    private Optional<SettledHit> hit() { return hits.stream().filter(h -> h.projectile().shot().projectileId().equals(captured.shot().projectileId())).findFirst(); }
    private double health() { return dragons.view().orElseThrow().target().currentHealth(); }
    private double credit() { return dragons.view().orElseThrow().contributions().values().stream().mapToDouble(v -> v.contributionDamage()).sum(); }
    private double distanceToParts(Vector3 p) {
        return dragon.getParts().stream().mapToDouble(part -> {
            var b=part.getBoundingBox(); return vector(p).distance(new Vector(Math.max(b.getMinX(),Math.min(b.getMaxX(),p.x())),
                    Math.max(b.getMinY(),Math.min(b.getMaxY(),p.y())),Math.max(b.getMinZ(),Math.min(b.getMaxZ(),p.z()))));
        }).min().orElseThrow();
    }
    private boolean validFrame(ArrowContinuity.Frame f) {
        var before=vector(f.before()); var after=vector(f.after());
        if (Math.abs(before.length()-after.length()) > 1e-9 || (before.length()>0 && before.angle(after)>Math.toRadians(6)+1e-6)
                || (f.groupLaunchAge()<3 && f.aim().isPresent()) || !f.profileRevision().equals("tracer-aimed/v3")) return false;
        return f.aim().map(a -> vector(captured.shot().initialVelocity()).angle(vector(a.point()).subtract(vector(captured.shot().launchPosition()))) <= Math.toRadians(30)+1e-6
                && a.distance()<=24).orElse(true);
    }
    private void journal() {
        c.observe(stage+"Flight", Map.of("projectile",captured.shot().projectileId().toString(),"launch",vec(captured.shot().launchPosition()),
                "direction",vec(captured.shot().initialVelocity()),"health",health(),"credit",credit(),"collision",collisions.getOrDefault(captured.shot().projectileId(),"NONE"),
                "currentPartDistancesAfterGrace",sampledDistances.getOrDefault(captured.shot().projectileId(),List.of()),
                "frames",path().stream().map(f -> Map.of("age",f.groupLaunchAge(),"position",vec(f.position()),"before",vec(f.before()),"after",vec(f.after()),
                        "aim",f.aim().map(a -> Map.of("point",vec(a.point()),"distance",a.distance(),"part",a.part().partId().toString())).orElse(Map.of()))).toList()));
    }
    private static Vector vector(Vector3 v) { return new Vector(v.x(),v.y(),v.z()); }
    private static List<Double> vec(Vector3 v) { return List.of(v.x(),v.y(),v.z()); }
    @EventHandler(priority=EventPriority.MONITOR) public void release(EntityShootBowEvent e) {
        if (e.getEntity().getUniqueId().equals(players.identity("alpha")) && e.getProjectile() instanceof Arrow arrow) latest=arrow;
    }
    @EventHandler(priority=EventPriority.MONITOR) public void collision(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow) collisions.put(e.getEntity().getUniqueId(), e.getHitEntity() instanceof EnderDragonPart ? "DRAGON" : e.getHitBlock()!=null ? "BLOCK" : "OTHER");
    }
}
