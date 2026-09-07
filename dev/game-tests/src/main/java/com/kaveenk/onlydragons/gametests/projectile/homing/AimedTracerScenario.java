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

/**
 * Native aimed-v3 acquisition and rejection controls against a production moving
 * dragon. Real use/release and post-launch turn packets are distinguished from
 * server launch-pose, inventory and wall setup. Current Bukkit part boxes and
 * native velocity are compared with captured continuity frames; the original
 * launch direction, never the player's later view, defines aim eligibility.
 * Creative mode isolates physics; this does not test Survival ammo or human feel.
 */
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

    /**
     * Subscribes to production settlements and owns reset, observer and reversible
     * wall cleanup before waiting for the real actor.
     * @param context isolated scenario report and server-thread scheduler
     * @throws Exception if setup cannot be initialized
     */
    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("aimed-tracer-v3"); players = new PlayerFixture(c);
        dragons = c.production().dragons(); c.listen(this);
        var observer = c.production().combat().observeSettled(hits::add);
        c.cleanup("aimed-tracer", () -> {
            finished = true; observer.close(); restoreWall();
            if (c.production().combat().activeCount() > 0) dragons.reset(dragons.generation().orElseThrow());
        });
        players.await("aimed tracer actor", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    /**
     * Resolves the declared actor's current live connection.
     * @return alpha's Player
     */
    private Player player() { return players.player("alpha"); }
    /**
     * Configures a disposable arena through the production setup API and equips a
     * safe Creative actor. Sampling starts before the first fresh ORBIT trial.
     * @throws Exception if persisted arena setup fails
     */
    private void setup() throws Exception {
        player().setGameMode(GameMode.CREATIVE); player().setAllowFlight(true); player().setFlying(true);
        player().setInvulnerable(true); player().getInventory().clear(); player().getInventory().setHeldItemSlot(0);
        players.permission("alpha", "onlydragons.fire", true);
        dragons.setup(new DevelopmentArena(player().getWorld().getKey().toString(), 160, 100, 160, 48, "test_dragon"));
        sample(); trial("aimed", 5, () -> shoot(this::aimed));
    }
    /**
     * Restores walls and resets the preceding generation before selecting the next
     * native dragon and trusted bow. The 180-tick allowance settles its real orbit;
     * each transition independently checks projectile/frame/ticket cleanup.
     * @param name action namespace and positive/control selection
     * @param level Tracer level; zero deliberately omits the enchantment
     * @param next stage after native orbit preparation
     */
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
    /**
     * Draws through a real use packet, sets the labelled pre-release pose and then
     * requests the real release. Side/behind controls remain within V range so radius
     * alone cannot explain rejection. Captured identity, full draw and gravity must
     * match the actual emitted arrow; no post-launch setup changes that arrow.
     * @param next stage after native release and capture
     */
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
    /**
     * Turns the player away after launch and proves the captured aimed shot still
     * assists into a moving native part. Independent literal outcomes are 900 domain
     * HP, 100 credit, one impact and 180 projected native HP, with bounded turning,
     * speed preservation and the three-tick launch grace.
     */
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
    /**
     * Turns a side/behind shot toward the target only after launch. Airborne proof
     * and within-V-radius samples make the ballistic miss a captured-cone control,
     * then advance to the next direction or level-I range control.
     */
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
    /**
     * Records the actual post-launch player view only while the original arrow is
     * still live and owned, preventing a turn observed after retirement from passing.
     */
    private void observeTurn() {
        turnTick = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        c.check(stage + "_turn_while_airborne", true, latest.isValid()
                && c.production().bows().projectile(captured.shot().projectileId()).isPresent());
        c.observe(stage + "Turn", Map.of("tick", turnTick, "yaw", player().getYaw(), "pitch", player().getPitch()));
    }
    /**
     * Requires a nonempty unsteered ballistic path, no settlement or HP/credit change,
     * and eventual registry retirement. The level-I control independently samples
     * current part distances beyond its four-block acquisition radius.
     * @param next stage after the negative outcome window
     */
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
    /**
     * Installs the reversible wall only after a real lock, requiring later ballistic
     * release and a native block collision without damage. Final reset checks actual
     * entity removal and all owned projectile/ticket resources before real quit.
     */
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
    /**
     * Samples continuity once per frame tick and compares current-tick applied
     * velocity with the native arrow. Counts matching live Bukkit part UUID/box witnesses;
     * distance samples come from independent nearest-box geometry after grace.
     * Only the obstruction stage mutates blocks, after a lock is observed.
     */
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
    /**
     * Places the bounded disposable stone wall ahead of the locked arrow, preserving
     * every prior block value for cleanup. This is explicit obstruction setup, not
     * native world generation or an arrow position change.
     * @param frame observed lock frame used to locate the barrier
     */
    private void installWall(ArrowContinuity.Frame frame) {
        int z = (int)Math.ceil(frame.position().z()+5);
        for (int x=112; x<=208; x++) for (int y=52; y<=148; y++) {
            var block = player().getWorld().getBlockAt(x,y,z); wall.put(block,block.getBlockData()); block.setType(Material.STONE,false);
        }
    }
    /**
     * Restores saved block data and clears wall ownership for both trial transition
     * and failure cleanup.
     */
    private void restoreWall() { wall.forEach((b,data) -> b.setBlockData(data,false)); wall.clear(); }
    /**
     * Selects only the current captured projectile's retained samples.
     * @return its ordered frame observations, or an empty list
     */
    private List<ArrowContinuity.Frame> path() { return paths.getOrDefault(captured.shot().projectileId(), List.of()); }
    /**
     * Finds the production physical settlement bound to the captured native UUID.
     * @return its first settlement if one occurred
     */
    private Optional<SettledHit> hit() { return hits.stream().filter(h -> h.projectile().shot().projectileId().equals(captured.shot().projectileId())).findFirst(); }
    /**
     * Reads production domain HP rather than using the native projection as oracle.
     * @return current managed target health
     */
    private double health() { return dragons.view().orElseThrow().target().currentHealth(); }
    /**
     * Reads the production contribution sum independently of native HP.
     * @return total credited damage in this one-actor trial
     */
    private double credit() { return dragons.view().orElseThrow().contributions().values().stream().mapToDouble(v -> v.contributionDamage()).sum(); }
    /**
     * Computes nearest distance by clamping to each actual Bukkit part box; it does
     * not reuse the production target-selection result.
     * @param p sampled arrow position
     * @return minimum distance to the live multipart geometry
     */
    private double distanceToParts(Vector3 p) {
        return dragon.getParts().stream().mapToDouble(part -> {
            var b=part.getBoundingBox(); return vector(p).distance(new Vector(Math.max(b.getMinX(),Math.min(b.getMaxX(),p.x())),
                    Math.max(b.getMinY(),Math.min(b.getMaxY(),p.y())),Math.max(b.getMinZ(),Math.min(b.getMaxZ(),p.z()))));
        }).min().orElseThrow();
    }
    /**
     * Checks literal six-degree turning, equal speed, three-tick grace and the pinned
     * profile. An assisted frame must also lie in the captured launch's 30-degree cone
     * and within V's 24-block retention bound.
     * @param f observed continuity frame
     * @return whether every applicable independent bound holds
     */
    private boolean validFrame(ArrowContinuity.Frame f) {
        var before=vector(f.before()); var after=vector(f.after());
        if (Math.abs(before.length()-after.length()) > 1e-9 || (before.length()>0 && before.angle(after)>Math.toRadians(6)+1e-6)
                || (f.groupLaunchAge()<3 && f.aim().isPresent()) || !f.profileRevision().equals("tracer-aimed/v3")) return false;
        return f.aim().map(a -> vector(captured.shot().initialVelocity()).angle(vector(a.point()).subtract(vector(captured.shot().launchPosition()))) <= Math.toRadians(30)+1e-6
                && a.distance()<=24).orElse(true);
    }
    /**
     * Publishes captured launch, collision, HP/credit and per-frame geometry under
     * the current trial namespace, preserving raw evidence for offline review.
     */
    private void journal() {
        c.observe(stage+"Flight", Map.of("projectile",captured.shot().projectileId().toString(),"launch",vec(captured.shot().launchPosition()),
                "direction",vec(captured.shot().initialVelocity()),"health",health(),"credit",credit(),"collision",collisions.getOrDefault(captured.shot().projectileId(),"NONE"),
                "currentPartDistancesAfterGrace",sampledDistances.getOrDefault(captured.shot().projectileId(),List.of()),
                "frames",path().stream().map(f -> Map.of("age",f.groupLaunchAge(),"position",vec(f.position()),"before",vec(f.before()),"after",vec(f.after()),
                        "aim",f.aim().map(a -> Map.of("point",vec(a.point()),"distance",a.distance(),"part",a.part().partId().toString())).orElse(Map.of()))).toList()));
    }
    /**
     * Copies an immutable domain vector into Bukkit's mutable vector representation.
     * @param v domain coordinates
     * @return a fresh native vector for independent geometry arithmetic
     */
    private static Vector vector(Vector3 v) { return new Vector(v.x(),v.y(),v.z()); }
    /**
     * Converts vector coordinates to report-compatible JSON values.
     * @param v sampled domain vector
     * @return ordered x, y and z components
     */
    private static List<Double> vec(Vector3 v) { return List.of(v.x(),v.y(),v.z()); }
    /**
     * Captures the actor's actual Arrow from the native bow event; later stages
     * require that UUID to exist in the production registry.
     * @param e native bow release
     */
    @EventHandler(priority=EventPriority.MONITOR) public void release(EntityShootBowEvent e) {
        if (e.getEntity().getUniqueId().equals(players.identity("alpha")) && e.getProjectile() instanceof Arrow arrow) latest=arrow;
    }
    /**
     * Records physical dragon-part, block or other collision by native arrow UUID.
     * @param e actual projectile-hit event, separate from domain settlement
     */
    @EventHandler(priority=EventPriority.MONITOR) public void collision(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow) collisions.put(e.getEntity().getUniqueId(), e.getHitEntity() instanceof EnderDragonPart ? "DRAGON" : e.getHitBlock()!=null ? "BLOCK" : "OTHER");
    }
}
