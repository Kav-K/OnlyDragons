package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/** Actual protocol input into the deployed producer; no manufactured events or test arrow adoption. */
public final class OwnedFiringScenario implements Scenario, Listener {
    private ScenarioContext context;
    private PlayerFixture players;
    private OwnedBowService bows;
    private DamageObservationProbe damage;
    private UUID encounter, secondEncounter;
    private boolean transferLaunch, immediateQuit, pendingAtQuit;
    private long observationSequence, disconnectRequestOrder, quitOrder, quitTick;
    private boolean kickObserved;
    private int launchVetoEvents, inputVetoEvents, permissionDeniedEvents, deaths, respawns;
    private OwnedProjectile immediateShot, transferShot;
    private final List<OwnedProjectile> emissions = new ArrayList<>();
    private final List<SettledHit> settlements = new ArrayList<>();
    private final Map<UUID, Long> releases = new HashMap<>();
    private Consumer<SettledHit> receiver;
    private final Map<UUID, Map<String, Object>> nativeLaunches = new LinkedHashMap<>();
    private final Map<UUID, EntityShootBowEvent> bowEvents = new LinkedHashMap<>();
    private final Map<UUID, List<Map<String, Object>>> nativeHits = new LinkedHashMap<>();
    private final Map<UUID, UUID> registeredTargets = new HashMap<>();
    private final Map<UUID, String> settlementPhases = new HashMap<>();
    private boolean retiredBeforeDelivery = true;
    private boolean nativeGuard;
    private boolean swap, cancelBow, cancelHit, cancelLaunch;
    private OwnedProjectile airborne;
    private int interactions;
    private Runnable beforeRelease = () -> {};
    private boolean cancelInput;
    private EnderDragon phaseTarget;
    private EnderDragon.Phase fixturePhase;
    private EnderDragon.Phase releasePhase;
    private final Map<UUID, Map<UUID, List<Double>>> partGeometry = new HashMap<>();
    @Override public void start(ScenarioContext context) throws Exception {
        this.context = context; context.mechanicRevision("owned-firing-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        bows = context.production().bows(); players = new PlayerFixture(context); damage = new DamageObservationProbe(context);
        context.listen(this); receiver = hit -> {
            retiredBeforeDelivery &= bows.projectile(hit.projectile().shot().projectileId()).isEmpty() && bows.arrow(hit.projectile().shot().projectileId()).isEmpty();
            Entity target = Bukkit.getEntity(registeredTargets.get(hit.impact().key().targetId()));
            settlementPhases.put(hit.projectile().shot().projectileId(), target instanceof EnderDragon dragon ? dragon.getPhase().name() : "NON_DRAGON");
            settlements.add(hit);
        }; bows.receiver(receiver);
        context.cleanup("owned-firing-service", () -> { if (encounter != null) bows.endEncounter(encounter); if (secondEncounter != null) bows.endEncounter(secondEncounter); bows.clearReceiver(receiver); });
        players.await("both production actors", 300, players::allOnline, this::setup);
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }
    private void setup() {
        World world = players.player("alpha").getWorld();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) context.tickChunk(world.getChunkAt(x, z));
        for (String actor : List.of("alpha", "beta")) {
            Player player = players.player(actor); player.setGameMode(GameMode.SURVIVAL); player.setAllowFlight(true); player.setFlying(true); player.setInvulnerable(true);
            player.getInventory().clear(); player.getInventory().setHeldItemSlot(0);
            players.setupPosition(actor, new Location(world, actor.equals("alpha") ? 0.5 : -5.5, 100, 0.5, 0, -70));
            kit(actor, "duplex", 20);
        }
        encounter = UUID.randomUUID();
        bows.openEncounter(encounter, world, new BoundingBox(-100, 60, -100, 100, 300, 100), new MechanicRevision("combat-calibration", "v1"));
        context.check("online_session_activation", true, bows.currentSession(players.identity("alpha")).isPresent()
                && bows.currentSession(players.identity("beta")).isPresent());
        context.check("two_real_owners", true, !players.identity("alpha").equals(players.identity("beta")));
        context.production().equipment().refresh(players.player("alpha")); swap = true;
        draw("alpha", "capture", () -> {
            swap = false; collect();
            var owned = owner("alpha"); context.check("native_primary_and_exact_duplex", 2, owned.size());
            airborne = owned.stream().filter(a -> a.shot().parentProjectileId().isEmpty()).findFirst().orElseThrow();
            var child = owned.stream().filter(a -> a.shot().parentProjectileId().isPresent()).findFirst().orElseThrow();
            context.check("shot_time_refresh_and_delayed_swap", true, owned.stream().allMatch(a -> a.shot().weapon().definitionId().equals("duplex")
                    && a.shot().stats().effective(StatKey.WEAPON_DAMAGE) == 107 && a.shot().enchantments().stream().anyMatch(e -> e.id().equals("duplex"))));
            context.check("duplex_captured_transform_roll_scale", true, child.shot().parentProjectileId().orElseThrow().equals(airborne.shot().projectileId())
                    && child.shot().launchPosition().equals(airborne.shot().launchPosition()) && child.shot().initialVelocity().equals(airborne.shot().initialVelocity())
                    && child.shot().crit() == airborne.shot().crit() && child.shot().projectileScale() == 0.2
                    && child.shot().launchTick() == airborne.shot().launchTick() + 1);
            var childLaunch = nativeLaunches.get(child.shot().projectileId());
            context.check("duplex_native_launch_matches_captured_transform", true,
                    vectorMatches(childLaunch.get("position"), airborne.shot().launchPosition())
                    && vectorMatches(childLaunch.get("velocity"), airborne.shot().initialVelocity())
                    && ((Number) childLaunch.get("tick")).longValue() == airborne.shot().launchTick() + 1);
            context.check("native_flight_continuous", true, bows.arrow(airborne.shot().projectileId()).orElseThrow().getLocation().toVector()
                    .distance(new org.bukkit.util.Vector(airborne.shot().launchPosition().x(), airborne.shot().launchPosition().y(), airborne.shot().launchPosition().z())) > 1);
            context.check("single_native_ammo_debit", 19, ammo("alpha"));
            context.check("pretarget_registry_identity", true, settlements.isEmpty() && bows.projectile(airborne.shot().projectileId()).orElseThrow().equals(airborne));
            Cow lateTarget = context.own(players.player("alpha").getWorld().spawn(new Location(players.player("alpha").getWorld(), 50, 100, 30), Cow.class));
            lateTarget.setAI(false); lateTarget.setGravity(false); register(lateTarget);
            context.check("later_target_registration_preserves_prefire_uuid", true, bows.projectile(airborne.shot().projectileId()).orElseThrow().equals(airborne)
                    && bows.projectile(child.shot().projectileId()).orElseThrow().equals(child));
            bows.unregisterTarget(lateTarget.getUniqueId()); lateTarget.remove();
            players.request("alpha", "reconnect");
            players.await("fresh alpha session", 150, () -> players.joins("alpha") == 2, this::reconnected);
        });
    }
    private void reconnected() {
        players.player("alpha").setGameMode(GameMode.SURVIVAL);
        players.player("alpha").setAllowFlight(true); players.player("alpha").setFlying(true); players.player("alpha").setInvulnerable(true);
        context.check("reconnected_survival_ammo_control", GameMode.SURVIVAL.name(), players.player("alpha").getGameMode().name());
        UUID replacement = bows.currentSession(players.identity("alpha")).orElseThrow();
        context.check("reconnect_invalidates_old_launch_session", true, !replacement.equals(airborne.sessionToken())
                && !bows.isCurrentSession(players.identity("alpha"), airborne.sessionToken())
                && bows.projectile(airborne.shot().projectileId()).orElseThrow().equals(airborne));
        bows.clearSession(players.identity("alpha"), airborne.sessionToken(), true);
        context.check("stale_clear_preserves_replacement", replacement.toString(), bows.currentSession(players.identity("alpha")).orElseThrow().toString());
        kit("alpha", "ordinary", 20);
        cancelBow = true; int before = bows.projectiles().size();
        draw("alpha", "cancel", () -> {
            cancelBow = false;
            context.check("final_cancelled_native_rolls_back", true, ammo("alpha") == 20 && bows.projectiles().size() == before && bows.reservedCapacity() == 0);
            shortbows();
        });
    }
    private void shortbows() {
        kit("beta", "shortbow_v1", 20);
        int before = owner("beta").size();
        players.request("beta", "hold");
        players.await("actual shortbow hold", 60, () -> players.player("beta").isHandRaised(), () -> context.later(24, () -> {
            players.request("beta", "release");
            context.later(5, () -> {
                collect(); var rows = owner("beta"); var primaries = rows.stream().filter(a -> a.shot().parentProjectileId().isEmpty()).toList();
                context.check("hold_emits_three_primary_child_groups", true, before == 0 && rows.size() == 6 && primaries.size() == 3);
                context.check("one_ten_tick_cadence", true, primaries.size() == 3 && primaries.get(1).shot().launchTick() - primaries.get(0).shot().launchTick() == 10
                        && primaries.get(2).shot().launchTick() - primaries.get(1).shot().launchTick() == 10);
                context.check("shortbow_hold_ammo_and_native_release", 17, ammo("beta"));
                context.check("registry_owner_isolation", true, rows.stream().allMatch(a -> a.shot().ownerId().equals(players.identity("beta")))
                        && bows.projectile(airborne.shot().projectileId()).orElseThrow().shot().ownerId().equals(players.identity("alpha")));
                context.later(10, this::clicks);
            });
        }));
    }
    private void clicks() {
        int before = owner("beta").size(); int interaction = interactions;
        players.request("beta", "click");
        players.await("shortbow left input", 60, () -> interactions > interaction, () -> context.later(3, () -> {
            collect(); context.check("left_click_uses_shortbow_route", before + 2, owner("beta").size());
            players.request("beta", "mixed");
            context.later(3, () -> {
                players.request("beta", "mixed-release");
                context.later(3, () -> {
                    collect(); context.check("mixed_input_no_extra_group", before + 2, owner("beta").size());
                    context.check("mixed_input_ammo_once", 16, ammo("beta"));
                    noAmmo();
                });
            });
        }));
    }
    private void noAmmo() {
        players.player("beta").getInventory().setItem(9, null); int before = owner("beta").size();
        players.request("beta", "empty");
        context.later(5, () -> {
            collect(); context.check("empty_ammo_no_emission_or_reservation", true, owner("beta").size() == before && bows.reservedCapacity() == 0);
            kit("beta", "shortbow_v1", 10); cancelLaunch = true; int vetoBefore = launchVetoEvents;
            players.request("beta", "launch-veto");
            context.later(5, () -> {
                cancelLaunch = false; collect(); context.check("launch_veto_refunds_and_releases", true, owner("beta").size() == before && ammo("beta") == 10
                        && bows.pendingGroups() == 0 && bows.reservedCapacity() == 0);
                context.check("actual_owned_launch_veto_observed", vetoBefore + 1, launchVetoEvents);
                players.player("beta").getInventory().setItemInOffHand(context.production().equipment().createLoadout("shortbow_v1"));
                players.request("beta", "offhand");
                context.later(4, () -> {
                    players.request("beta", "offhand-release");
                    context.later(4, () -> { collect(); context.check("offhand_does_not_duplicate", before, owner("beta").size()); context.check("offhand_ammo_unchanged", 10, ammo("beta")); inputControls(); });
                });
            });
        });
    }
    private void inputControls() {
        int before = owner("beta").size(); cancelInput = true; int vetoBefore = inputVetoEvents;
        players.request("beta", "input-veto");
        context.later(5, () -> {
            cancelInput = false;
            context.check("final_input_veto_no_debit", true, owner("beta").size() == before && ammo("beta") == 10);
            context.check("actual_input_veto_observed", vetoBefore + 1, inputVetoEvents);
            var permission = players.permission("beta", "onlydragons.fire", false);
            int deniedBefore = permissionDeniedEvents;
            players.request("beta", "permission-denied");
            context.later(5, () -> {
                context.check("fire_permission_denial", true, owner("beta").size() == before && ammo("beta") == 10);
                context.check("actual_permission_denied_input_observed", deniedBefore + 1, permissionDeniedEvents);
                players.removePermission(permission);
                players.request("beta", "recovery");
                players.await("successful shortbow after negative controls", 60, () -> owner("beta").size() == before + 2, () -> {
                    context.check("shortbow_recovery_group_and_debit", true, owner("beta").size() == before + 2 && ammo("beta") == 9);
                    transfer();
                });
            });
        });
    }
    private void transfer() {
        World world = players.player("beta").getWorld(); secondEncounter = UUID.randomUUID();
        bows.openEncounter(secondEncounter, world, new BoundingBox(200, 60, -100, 400, 300, 100), new MechanicRevision("combat-calibration", "v1"));
        kit("beta", "duplex", 10); transferLaunch = true;
        draw("beta", "transfer", () -> {
            context.check("direct_arena_transfer_replaces_session", true, !bows.isCurrentSession(players.identity("beta"), transferShot.sessionToken())
                    && bows.currentSession(players.identity("beta")).isPresent());
            context.check("transfer_cancels_only_delayed_child", true, bows.projectile(transferShot.shot().projectileId()).orElseThrow().equals(transferShot)
                    && bows.projectiles().stream().filter(a -> a.shot().shotId().equals(transferShot.shot().shotId())).count() == 1
                    && bows.reservedCapacity() == 0 && ammo("beta") == 9);
            immediateQuit = true;
            draw("beta", "immediate", () -> players.await("immediate reconnect completed", 150, () -> players.joins("beta") == 2, () -> {
                Player beta = players.player("beta"); beta.setGameMode(GameMode.SURVIVAL); beta.setAllowFlight(true); beta.setFlying(true); beta.setInvulnerable(true);
                context.check("post_shot_disconnect_request_precedes_actual_quit", true, disconnectRequestOrder > 0 && quitOrder > disconnectRequestOrder
                        && quitTick >= immediateShot.shot().launchTick());
                context.check("actual_quit_retains_primary_and_single_debit", true,
                        bows.projectile(immediateShot.shot().projectileId()).orElseThrow().equals(immediateShot) && ammo("beta") == 8);
                var group = emissions.stream().filter(a -> a.shot().shotId().equals(immediateShot.shot().shotId())).toList();
                context.check("no_group_emission_after_actual_quit", true, !group.isEmpty() && group.stream().allMatch(a ->
                        ((Number) nativeLaunches.get(a.shot().projectileId()).get("sequence")).longValue() < quitOrder));
                long children = group.stream().filter(a -> a.shot().parentProjectileId().isPresent()).count();
                context.check("disconnect_child_count_matches_observed_boundary", pendingAtQuit ? 0L : 1L, children);
                context.observe("postShotDisconnectResult", Map.of("pendingAtQuit", pendingAtQuit, "childrenBeforeQuit", children,
                        "quitTick", quitTick, "kickEventObserved", kickObserved,
                        "scope", "Real client disconnect/quit after native launch; synthetic adapter regression separately covers clearSession before delayed settlement."));
                context.check("immediate_quit_old_token_inactive", true, !bows.isCurrentSession(players.identity("beta"), immediateShot.sessionToken()));
                deathAfterExit();
            }));
        });
    }
    private void deathAfterExit() {
        Player beta = players.player("beta"); UUID owner = beta.getUniqueId();
        players.setupPosition("beta", new Location(beta.getWorld(), 500, 100, 0.5));
        players.await("session cleared outside both arenas", 60, () -> bows.currentSession(owner).isEmpty(), () -> {
            var retained = owner("beta"); var alpha = owner("alpha");
            context.check("exit_retains_airborne_before_death", true, !retained.isEmpty()
                    && retained.stream().allMatch(a -> bows.arrow(a.shot().projectileId()).map(Arrow::isValid).orElse(false)));
            context.observe("ownerDeathSetup", "Public setHealth(0) after actual arena-exit session cleanup; real PlayerDeathEvent, then declared packet respawn. No client attack claim.");
            beta.setInvulnerable(false); beta.setHealth(0);
            context.check("actual_death_without_session_retires_owned", true, deaths == 1 && owner("beta").isEmpty()
                    && retained.stream().allMatch(a -> bows.arrow(a.shot().projectileId()).isEmpty())
                    && bows.reservedCapacity() == 0 && bows.pendingClaims() == 0);
            context.check("owner_death_preserves_other_registry", true, alpha.equals(owner("alpha")));
            context.later(3, () -> {
                players.request("beta", "respawn-after-exit");
                players.await("real respawn after sessionless death", 100, () -> respawns == 1 && !players.player("beta").isDead(), () -> {
                    Player current = players.player("beta"); current.setAllowFlight(true); current.setFlying(true); current.setInvulnerable(true);
                    context.check("post_death_respawn_and_no_stale_delivery", true, current.getHealth() > 0 && retained.stream().noneMatch(a ->
                            settlements.stream().anyMatch(h -> h.projectile().shot().projectileId().equals(a.shot().projectileId()))));
                    dragon(0);
                });
            });
        });
    }
    private static boolean vectorMatches(Object raw, Vector3 expected) {
        if (!(raw instanceof List<?> values) || values.size() != 3) return false;
        double[] target = {expected.x(), expected.y(), expected.z()};
        for (int i = 0; i < 3; i++) if (!(values.get(i) instanceof Number number) || Math.abs(number.doubleValue() - target[i]) > 1e-9) return false;
        return true;
    }
    private void dragon(int index) {
        if (index == 6) { volley(); return; }
        var phase = switch (index) { case 2 -> EnderDragon.Phase.CIRCLING; case 3 -> EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET;
            case 4 -> EnderDragon.Phase.BREATH_ATTACK; default -> EnderDragon.Phase.HOVER; };
        kit("alpha", "ordinary", 20);
        players.request("alpha", "dragon-" + index + "-use");
        players.await("dragon trial draw " + index, 60, () -> players.player("alpha").isHandRaised(), () -> context.later(22, () -> {
            World world = players.player("alpha").getWorld();
            EnderDragon dragon = context.own(world.spawn(new Location(world, 0.5, 100, 12.5), EnderDragon.class,
                    d -> { d.setPersistent(false); d.setPhase(EnderDragon.Phase.HOVER); }));
            phaseTarget = dragon; fixturePhase = EnderDragon.Phase.HOVER;
            damage.watch(dragon); register(dragon);
            context.later(2, () -> {
                var parts = dragon.getParts().stream().map(p -> (EnderDragonPart) p)
                        .sorted(Comparator.comparingDouble((EnderDragonPart p) -> p.getBoundingBox().getWidthX())
                                .thenComparingDouble(p -> p.getBoundingBox().getCenterZ()).thenComparingDouble(p -> p.getBoundingBox().getCenterX())).toList();
                var aim = index == 0 ? parts.getFirst() : index == 1 ? parts.get(1) : parts.getLast();
                Location position = aim.getBoundingBox().getCenter().toLocation(world)
                        .add(0, -players.player("alpha").getEyeHeight(), index == 2 ? -3 : -8);
                position.setYaw(0); position.setPitch(0);
                players.setupPosition("alpha", position);
                context.observe("dragonSetup_" + index, Map.of("target", dragon.getUniqueId().toString(), "age", dragon.getTicksLived(),
                        "parts", parts.stream().map(p -> Map.of("part", p.getUniqueId().toString(), "box", p.getBoundingBox().toString())).toList()));
                cancelHit = index == 5; releasePhase = phase;
                int before = settlements.size(); double hp = dragon.getHealth();
                long releasesBefore = releases.getOrDefault(players.identity("alpha"), 0L);
                players.request("alpha", "dragon-" + index + "-release");
                players.await("dragon native release " + index, 60, () -> releases.getOrDefault(players.identity("alpha"), 0L) > releasesBefore,
                        () -> context.later(11, () -> {
                List<SettledHit> hits = settlements.subList(before, settlements.size());
                context.observe("dragon_" + index, hits.stream().map(h -> Map.of("projectile", h.projectile().shot().projectileId().toString(),
                        "part", h.impact().targetPart().orElse("none"), "collisionTick", h.collisionTick(), "settlementTick", h.settlementTick(),
                        "rejection", h.rejection().map(Enum::name).orElse("ACCEPTED"))).toList());
                context.check("dragon_terminal_claim_" + index, 1, hits.size());
                if (!hits.isEmpty()) {
                    SettledHit hit = hits.getFirst();
                    context.check("dragon_policy_" + index, true, index == 4 ? hit.rejection().orElseThrow() == SettledHit.Rejection.UNSUPPORTED_PHASE
                            : index == 5 ? hit.rejection().orElseThrow() == SettledHit.Rejection.PHYSICAL_VETO : hit.accepted());
                    context.check("dragon_parent_part_" + index, true, dragon.getParts().stream().anyMatch(p -> p.getUniqueId().toString().equals(hit.impact().targetPart().orElse("none"))));
                    var observed = nativeHits.get(hit.projectile().shot().projectileId()).getFirst();
                    context.check("observed_collision_phase_" + index, phase.name(), observed.get("phase"));
                    context.check("observed_settlement_phase_" + index, phase.name(), settlementPhases.get(hit.projectile().shot().projectileId()));
                    context.check("uniform_part_scale_" + index, 1.0, hit.partScale());
                    if (index < 2) context.check("actual_part_geometry_" + index, index == 0 ? List.of(1.0, 1.0) : List.of(5.0, 3.0),
                            partGeometry.get(hit.projectile().shot().projectileId()).get(UUID.fromString(hit.impact().targetPart().orElseThrow())));
                    context.check("dragon_retired_" + index, true, bows.projectile(hit.projectile().shot().projectileId()).isEmpty()
                            && bows.arrow(hit.projectile().shot().projectileId()).isEmpty() && hit.settlementTick() > hit.collisionTick());
                }
                context.observe("native_trial_" + index, Map.of("launches", List.copyOf(nativeLaunches.values()), "hits", nativeHits.values().stream().flatMap(List::stream).toList()));
                if (index == 3 && !hits.isEmpty()) context.check("seated_physical_claim_without_native_damage_event", 0L,
                        damage.events().stream().filter(e -> e.directDamager().equals(hits.getFirst().projectile().shot().projectileId())).count());
                context.check("native_hp_suppressed_" + index, hp, dragon.getHealth());
                cancelHit = false; bows.unregisterTarget(dragon.getUniqueId()); dragon.remove(); dragon(index + 1);
                }));
            });
        }));
    }
    private void volley() {
        World world = players.player("alpha").getWorld();
        EnderDragon dragon = context.own(world.spawn(new Location(world, 0.5, 100, 12.5), EnderDragon.class,
                d -> { d.setPersistent(false); d.setPhase(EnderDragon.Phase.HOVER); }));
        register(dragon); damage.watch(dragon);
        context.later(2, () -> {
            var aim = dragon.getParts().stream().filter(p -> p.getBoundingBox().getWidthX() == 5).findFirst().orElseThrow();
            for (String actor : List.of("alpha", "beta")) {
                Location location = aim.getBoundingBox().getCenter().toLocation(world).add(actor.equals("alpha") ? -0.7 : 0.7,
                        -players.player(actor).getEyeHeight(), -8);
                location.setYaw(0); location.setPitch(0); players.setupPosition(actor, location); kit(actor, "ordinary", 10);
                players.request(actor, "volley-use");
            }
            players.await("both real drawn inputs", 60, () -> players.player("alpha").isHandRaised() && players.player("beta").isHandRaised(),
                    () -> context.later(22, () -> {
                        int before = settlements.size();
                        players.request("alpha", "volley-release"); players.request("beta", "volley-release");
                        players.await("both settled physical owners", 80, () -> settlements.size() >= before + 2, () -> {
                            var hits = List.copyOf(settlements.subList(before, settlements.size()));
                            context.check("simultaneous_two_owner_claims", true, hits.size() == 2 && hits.stream().allMatch(SettledHit::accepted)
                                    && hits.stream().map(h -> h.projectile().shot().ownerId()).distinct().count() == 2
                                    && hits.stream().map(SettledHit::collisionTick).distinct().count() == 1);
                            context.check("two_owner_single_ammo_alpha", 9, ammo("alpha"));
                            context.check("two_owner_single_ammo_beta", 9, ammo("beta"));
                            context.observe("twoOwnerVolley", hits.stream().map(h -> Map.of("owner", h.projectile().shot().ownerId().toString(),
                                    "projectile", h.projectile().shot().projectileId().toString(), "collisionTick", h.collisionTick(),
                                    "settlementTick", h.settlementTick())).toList());
                            bows.unregisterTarget(dragon.getUniqueId()); dragon.remove(); nativeVeto();
                        });
                    }));
        });
    }
    private void register(LivingEntity entity) {
        UUID target = UUID.randomUUID(); registeredTargets.put(target, entity.getUniqueId()); bows.registerTarget(encounter, target, entity);
    }
    private void nativeVeto() {
        World world = players.player("alpha").getWorld();
        players.setupPosition("alpha", new Location(world, 0.5, 100, 0.5, 0, 0)); kit("alpha", "ordinary", 10);
        Cow cow = context.own(world.spawn(new Location(world, 0.5, 100.5, 12), Cow.class)); cow.setAI(false); cow.setGravity(false);
        register(cow); damage.watch(cow); double hp = cow.getHealth(); int before = settlements.size(); nativeGuard = true;
        context.observe("nativeGuardSetup", "After native launch and group settlement, the fixture changes only the owned arrow's native base damage to 2. A LOW native-damage listener vetoes the actual collision before production suppression. No event or collision is manufactured.");
        draw("alpha", "native-veto", () -> context.later(10, () -> {
            nativeGuard = false; var hits = settlements.subList(before, settlements.size());
            context.check("native_veto_one_terminal_claim", 1, hits.size());
            if (!hits.isEmpty()) {
                var hit = hits.getFirst(); var events = damage.events().stream().filter(e -> hit.projectile().shot().projectileId().equals(e.directDamager())).toList();
                context.check("observed_pre_suppression_native_veto", true, hit.rejection().orElseThrow() == SettledHit.Rejection.NATIVE_VETO
                        && events.size() == 1 && events.getFirst().initialDamage() > 0 && events.getFirst().cancelled()
                        && events.getFirst().settledDamage() == 0 && events.getFirst().finalDamage() == 0);
                context.check("native_veto_health_cohort", true, damage.healthCohorts().stream().anyMatch(c -> c.target().equals(cow.getUniqueId())
                        && c.health() == hp && c.eventSequences().equals(List.of(events.getFirst().sequence()))));
            }
            context.check("native_veto_preserves_hp", hp, cow.getHealth()); bows.unregisterTarget(cow.getUniqueId()); cow.remove(); finish();
        }));
    }
    private void finish() {
        context.check("receiver_follows_terminal_retirement", true, retiredBeforeDelivery);
        context.check("all_native_launches_owned_by_actual_shooter", true, nativeLaunches.values().stream().allMatch(row -> Boolean.TRUE.equals(row.get("ownedAtLaunch"))));
        Set<UUID> emitted = new HashSet<>(bows.trace().stream().filter(t -> t.kind().equals("emitted")).map(OwnedBowService.Trace::projectileId).toList());
        Set<UUID> provisional = new HashSet<>();
        bowEvents.forEach((id, event) -> { if (event.isCancelled() && !nativeLaunches.containsKey(id) && emitted.contains(id)) provisional.add(id); });
        Set<UUID> classified = new HashSet<>(nativeLaunches.keySet()); classified.addAll(provisional);
        context.check("every_emission_has_native_launch_or_final_bow_veto", true, emitted.equals(classified));
        context.check("final_bow_veto_provisional_only", true, provisional.size() == 1 && provisional.stream().allMatch(id -> {
            var primary = emissions.stream().filter(a -> a.shot().projectileId().equals(id)).findFirst().orElseThrow();
            return primary.shot().parentProjectileId().isEmpty() && bows.projectile(id).isEmpty() && bows.arrow(id).isEmpty()
                    && !nativeHits.containsKey(id) && settlements.stream().noneMatch(h -> h.projectile().shot().projectileId().equals(id))
                    && emissions.stream().noneMatch(a -> a.shot().parentProjectileId().filter(id::equals).isPresent())
                    && bows.trace().stream().anyMatch(t -> t.projectileId().equals(id) && t.kind().equals("retired") && t.detail().equals("FAILED_LAUNCH"));
        }));
        context.observe("bowEvents", bowEvents.entrySet().stream().map(e -> Map.of("projectile", e.getKey().toString(),
                "owner", e.getValue().getEntity().getUniqueId().toString(), "finalCancelled", e.getValue().isCancelled(),
                "nativeLaunch", nativeLaunches.containsKey(e.getKey()), "provisionalOnly", provisional.contains(e.getKey()))).toList());
        context.check("claims_match_independent_native_ownership", true, settlements.stream().allMatch(hit -> {
            UUID id = hit.projectile().shot().projectileId(); var launch = nativeLaunches.get(id); var physical = nativeHits.get(id).getFirst();
            return launch != null && launch.get("shooter").equals(hit.projectile().shot().ownerId().toString())
                    && physical.get("shooter").equals(hit.projectile().shot().ownerId().toString())
                    && physical.get("parent").equals(registeredTargets.get(hit.impact().key().targetId()).toString())
                    && (hit.impact().targetPart().isEmpty() || physical.get("part").equals(hit.impact().targetPart().orElseThrow()));
        }));
        context.observe("nativeLaunches", List.copyOf(nativeLaunches.values()));
        context.observe("nativeHits", nativeHits.values().stream().flatMap(List::stream).toList());
        context.observe("emissions", emissions.stream().map(a -> Map.of("projectile", a.shot().projectileId().toString(), "owner", a.shot().ownerId().toString(),
                "session", a.sessionToken().toString(), "shot", a.shot().shotId().toString(), "parent", a.shot().parentProjectileId().map(UUID::toString).orElse("none"),
                "tick", a.shot().launchTick())).toList());
        bows.endEncounter(encounter); bows.endEncounter(secondEncounter);
        context.check("reset_releases_owned_state", true, bows.capacityUsed() == 0 && bows.pendingGroups() == 0 && bows.pendingClaims() == 0);
        players.request("alpha", "end"); players.request("beta", "end");
        players.await("both quits", 100, () -> players.quits("alpha") == 2 && players.quits("beta") == 2, () -> {
            context.check("quit_sessions_released", true, bows.currentSession(players.identity("alpha")).isEmpty() && bows.currentSession(players.identity("beta")).isEmpty());
            context.finish();
        });
    }
    private void draw(String actor, String prefix, ScenarioContext.Step next) {
        long before = releases.getOrDefault(players.identity(actor), 0L);
        players.request(actor, prefix + "-use");
        players.await("native drawn hold " + prefix, 60, () -> players.player(actor).isHandRaised(), () -> context.later(22, () -> {
            context.later(2, () -> {
            beforeRelease.run(); beforeRelease = () -> {};
            players.request(actor, prefix + "-release");
            players.await("native release " + prefix, 60, () -> releases.getOrDefault(players.identity(actor), 0L) > before, () -> context.later(3, next));
            });
        }));
    }
    private void kit(String actor, String id, int ammo) {
        players.player(actor).getInventory().all(Material.ARROW).keySet().forEach(slot -> players.player(actor).getInventory().setItem(slot, null));
        players.setupItem(actor, 0, context.production().equipment().createLoadout(id));
        players.setupItem(actor, 9, new ItemStack(Material.ARROW, ammo)); players.player(actor).updateInventory();
    }
    private int ammo(String actor) { return players.player(actor).getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum(); }
    private void collect() { for (var a : bows.projectiles()) if (emissions.stream().noneMatch(old -> old.shot().projectileId().equals(a.shot().projectileId()))) emissions.add(a); }
    private List<OwnedProjectile> owner(String actor) { return bows.projectiles().stream().filter(a -> a.shot().ownerId().equals(players.identity(actor))).toList(); }
    @EventHandler(priority = EventPriority.LOWEST) public void beforeBow(EntityShootBowEvent event) {
        if (swap && event.getEntity().getUniqueId().equals(players.identity("alpha"))) context.production().equipment().bonus((Player) event.getEntity(), StatKey.WEAPON_DAMAGE, 7);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void bow(EntityShootBowEvent event) {
        bowEvents.put(event.getProjectile().getUniqueId(), event);
        releases.merge(event.getEntity().getUniqueId(), 1L, Long::sum);
        if (swap && event.getEntity().getUniqueId().equals(players.identity("alpha"))) players.setupItem("alpha", 0, context.production().equipment().createLoadout("fatal_tempo"));
        if (cancelBow && event.getEntity().getUniqueId().equals(players.identity("alpha"))) event.setCancelled(true);
        collect();
        if (event.getEntity().getUniqueId().equals(players.identity("beta")) && !event.isCancelled()) {
            if (transferLaunch) {
                transferLaunch = false; transferShot = bows.projectile(event.getProjectile().getUniqueId()).orElseThrow();
                players.setupPosition("beta", new Location(players.player("beta").getWorld(), 250, 100, 0.5, 0, -70));
            } else if (immediateQuit) {
                immediateQuit = false; immediateShot = bows.projectile(event.getProjectile().getUniqueId()).orElseThrow();
            }
        }
        if (releasePhase != null && event.getEntity().getUniqueId().equals(players.identity("alpha"))) {
            fixturePhase = releasePhase; releasePhase = null; phaseTarget.setPhase(fixturePhase);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void usedBow(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() == Statistic.USE_ITEM && event.getMaterial() == Material.BOW
                && event.getPlayer().getUniqueId().equals(players.identity("beta")) && immediateShot != null && players.joins("beta") == 1) {
            disconnectRequestOrder = ++observationSequence;
            context.observe("postShotDisconnectRequest", Map.of("tick", Bukkit.getCurrentTick(), "sequence", disconnectRequestOrder,
                    "statistic", event.getStatistic().name(), "material", event.getMaterial().name(), "pendingGroups", bows.pendingGroups(),
                    "validPrimary", bows.arrow(immediateShot.shot().projectileId()).map(Arrow::isValid).orElse(false)));
            players.request("beta", "reconnect");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void kick(PlayerKickEvent event) {
        if (!event.getPlayer().getUniqueId().equals(players.identity("beta"))) return;
        kickObserved = true;
        context.observe("immediateKickEvent", Map.of(
                "tick", Bukkit.getCurrentTick(), "cause", event.getCause().name(), "cancelled", event.isCancelled(),
                "reason", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.reason())));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void quit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(players.identity("beta")) && immediateShot != null && players.joins("beta") == 1) {
            boolean validPrimary = bows.arrow(immediateShot.shot().projectileId()).map(Arrow::isValid).orElse(false);
            pendingAtQuit = bows.pendingGroups() > 0 && validPrimary;
            quitOrder = ++observationSequence; quitTick = Integer.toUnsignedLong(Bukkit.getCurrentTick());
            context.observe("immediateQuit", Map.of("tick", quitTick, "sequence", quitOrder, "launchTick", immediateShot.shot().launchTick(), "pendingGroups", bows.pendingGroups(), "validPrimary", validPrimary));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void interact(PlayerInteractEvent event) {
        interactions++;
        if (!event.getPlayer().getUniqueId().equals(players.identity("beta")) || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (cancelInput) {
            event.setUseItemInHand(Event.Result.DENY); inputVetoEvents++;
            context.observe("inputVetoEvent", Map.of("owner", event.getPlayer().getUniqueId().toString(),
                    "tick", Bukkit.getCurrentTick(), "action", event.getAction().name(), "hand", event.getHand().name(),
                    "useItem", event.useItemInHand().name()));
        }
        if (!event.getPlayer().hasPermission("onlydragons.fire")) {
            permissionDeniedEvents++;
            context.observe("permissionDeniedEvent", Map.of("owner", event.getPlayer().getUniqueId().toString(),
                    "tick", Bukkit.getCurrentTick(), "action", event.getAction().name(), "hand", event.getHand().name(),
                    "permission", false));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void launch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !(arrow.getShooter() instanceof Player shooter)) return;
        if (cancelLaunch) { event.setCancelled(true); if (shooter.getUniqueId().equals(players.identity("beta")) && bows.projectile(arrow.getUniqueId()).isPresent()) launchVetoEvents++; }
        var owned = bows.projectile(arrow.getUniqueId());
        nativeLaunches.put(arrow.getUniqueId(), Map.of("projectile", arrow.getUniqueId().toString(), "shooter", shooter.getUniqueId().toString(),
                "sequence", ++observationSequence,
                "tick", Bukkit.getCurrentTick(), "ownedAtLaunch", owned.isPresent() && owned.get().shot().ownerId().equals(shooter.getUniqueId()),
                "position", List.of(arrow.getLocation().getX(), arrow.getLocation().getY(), arrow.getLocation().getZ()), "velocity", List.of(arrow.getVelocity().getX(), arrow.getVelocity().getY(), arrow.getVelocity().getZ()),
                "damage", arrow.getDamage(), "critical", arrow.isCritical(), "cancelled", event.isCancelled()));
        if (nativeGuard) context.later(2, () -> { if (arrow.isValid()) arrow.setDamage(2); });
        collect();
    }
    @EventHandler(priority = EventPriority.LOW) public void nativeDamage(EntityDamageByEntityEvent event) {
        if (nativeGuard && event.getDamager() instanceof Arrow arrow && bows.projectile(arrow.getUniqueId()).isPresent()) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH) public void death(PlayerDeathEvent event) {
        if (event.getPlayer().getUniqueId().equals(players.identity("beta"))) { deaths++; event.getDrops().clear(); event.setDroppedExp(0); }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getUniqueId().equals(players.identity("beta"))) respawns++;
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void phase(EnderDragonChangePhaseEvent event) {
        // Declared server setup keeps each isolated trial in its named phase during native drawing.
        if (event.getEntity().equals(phaseTarget) && event.getNewPhase() != fixturePhase) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void hit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !(arrow.getShooter() instanceof Player shooter)) return;
        Entity actual = event.getHitEntity();
        if (cancelHit && actual instanceof EnderDragonPart) event.setCancelled(true);
        Entity parent = actual instanceof EnderDragonPart part ? part.getParent() : actual;
        if (actual instanceof EnderDragonPart part) partGeometry.computeIfAbsent(arrow.getUniqueId(), ignored -> new HashMap<>())
                .putIfAbsent(part.getUniqueId(), List.of(part.getBoundingBox().getWidthX(), part.getBoundingBox().getHeight()));
        nativeHits.computeIfAbsent(arrow.getUniqueId(), ignored -> new ArrayList<>()).add(Map.of(
                "projectile", arrow.getUniqueId().toString(), "shooter", shooter.getUniqueId().toString(), "tick", Bukkit.getCurrentTick(),
                "part", actual == null ? "block" : actual.getUniqueId().toString(), "parent", parent == null ? "block" : parent.getUniqueId().toString(),
                "phase", parent instanceof EnderDragon dragon ? dragon.getPhase().name() : "NON_DRAGON", "cancelled", event.isCancelled(),
                "dimensions", actual == null ? List.of() : List.of(actual.getBoundingBox().getWidthX(), actual.getBoundingBox().getHeight()),
                "position", List.of(arrow.getLocation().getX(), arrow.getLocation().getY(), arrow.getLocation().getZ())));
    }
}
