package com.kaveenk.onlydragons.gametests.fixtures;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Two-actor cookbook proving packets through native events and independently sampled effects.
 * The sequence separates command permissions, tagged inventory transactions, movement,
 * melee veto/acceptance, physical arrows, death/respawn and reconnect. Setup teleports,
 * items and API-induced death are labeled; they never count as client input. The
 * damage probe is calibrated against native HP, not used to invent production credit.
 */
public final class PlayerPrimitivesScenario implements Scenario, Listener {
    private ScenarioContext context;
    private PlayerFixture players;
    private DamageObservationProbe damage;
    private Cow melee, arrowTarget;
    private Block lever;
    private boolean cancelDamage = true;
    private final Map<String, Integer> events = new HashMap<>();
    private PermissionAttachment calibration;
    private double healthBefore;
    private double arrowHealthBefore;
    private Player originalAlpha;
    private UUID releasedArrow;
    private ItemStack managedBow;
    private final List<Integer> clickedSlots = new ArrayList<>();
    private final List<Double> damageHealthBefore = new ArrayList<>(), damageHealthAfter = new ArrayList<>();
    private static final double CANCELLED_FIXTURE_DAMAGE = 7.0;

    /**
     * Registers the strict actor plan, bounded damage probe and scenario timeout.
     * @param context server-thread cleanup/report owner
     * @throws Exception if the isolated actor plan cannot be admitted
     */
    @Override public void start(ScenarioContext context) throws Exception {
        this.context = context;
        context.mechanicRevision("headless-primitives-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.observe("scope", "Real offline player packet input and native Paper events. Server setup is labelled. Does not certify the deferred production dragon physical adapter.");
        context.observe("cancelledDamageSetup", "The test listener changes the already-rejected native hit to 7 damage before cancellation, solely to distinguish LOWEST initial and MONITOR settled observations; no health is consumed.");
        players = new PlayerFixture(context);
        damage = new DamageObservationProbe(context);
        context.listen(this);
        players.await("two real player joins", 300, players::allOnline, this::setup);
        context.later(2200, () -> context.fail(new IllegalStateException("Player primitive fixture deadline exceeded")));
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }
    /**
     * Builds reversible native targets/lever and begins denied then permitted command trials.
     */
    private void setup() {
        context.check("two_distinct_real_players", true, !players.identity("alpha").equals(players.identity("beta"))
                && Bukkit.getOnlinePlayers().size() == 2);
        context.check("loopback_nonop_players", true, List.of("alpha", "beta").stream().allMatch(id -> {
            Player player = players.player(id);
            return !player.isOp() && player.getAddress() != null && player.getAddress().getAddress().isLoopbackAddress();
        }));
        originalAlpha = players.player("alpha");
        World world = originalAlpha.getWorld();
        for (String id : List.of("alpha", "beta")) {
            Player player = players.player(id);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(true); player.setFlying(true);
            player.setInvulnerable(false);
            player.getInventory().clear();
            players.setupPosition(id, new Location(world, id.equals("alpha") ? 0.5 : -1.5, 100, 0.5, 25, 0));
        }
        context.tickChunk(world.getChunkAt(0, 0));
        context.tickChunk(world.getChunkAt(-1, 0));
        melee = context.own(world.spawn(new Location(world, 0.5, 100, 2.2), Cow.class));
        arrowTarget = context.own(world.spawn(new Location(world, 0.5, 100.5, 8), Cow.class));
        for (Cow cow : List.of(melee, arrowTarget)) { cow.setAI(false); cow.setGravity(false); damage.watch(cow); }
        lever = world.getBlockAt(1, 100, 2);
        var oldLever = lever.getBlockData().clone();
        Block support = world.getBlockAt(1, 99, 2);
        var oldSupport = support.getBlockData().clone();
        context.cleanup("lever-blocks", () -> { lever.setBlockData(oldLever, false); support.setBlockData(oldSupport, false); });
        support.setType(Material.STONE);
        lever.setType(Material.LEVER);
        Switch data = (Switch) lever.getBlockData();
        data.setAttachedFace(FaceAttachable.AttachedFace.FLOOR); data.setFacing(BlockFace.NORTH);
        lever.setBlockData(data);
        players.bind("melee", melee.getUniqueId()); players.bind("arrow", arrowTarget.getUniqueId());
        after(15, () -> command("denied", () -> {
            context.check("packet_permission_denial_no_grant", true, originalAlpha.getInventory().isEmpty());
            calibration = players.permission("alpha", "onlydragons.calibration", true);
            command("grant", () -> {
                context.check("packet_command_granted_managed_item", Material.BOW.name(), originalAlpha.getInventory().getItem(0).getType().name());
                players.removePermission(calibration);
                command("stats", this::equipment);
            });
        }));
    }
    /**
     * Waits for a new actual command event before advancing to post-dispatch checks.
     */
    private void command(String id, ScenarioContext.Step next) {
        int previous = count("command");
        players.request("alpha", id);
        players.await("command packet " + id, 60, () -> count("command") > previous, () -> after(2, next));
    }
    /**
     * Checks the production stat result, then stages exact tagged-bow inventory inputs.
     */
    private void equipment() {
        context.check("three_real_command_events", 3, count("command"));
        var inspection = context.production().equipment().refresh(originalAlpha);
        context.check("deployed_stats_api_exact_weapon_damage", 100.0, inspection.stats().snapshot().effective(StatKey.WEAPON_DAMAGE));
        players.setupItem("alpha", 1, originalAlpha.getInventory().getItem(0).clone());
        originalAlpha.getInventory().setItem(0, null);
        players.setupItem("alpha", 2, new ItemStack(Material.STONE, 2));
        players.setupItem("alpha", 9, new ItemStack(Material.ARROW, 8));
        managedBow = originalAlpha.getInventory().getItem(1).clone();
        originalAlpha.updateInventory();
        after(5, () -> inventoryClick(0));
    }
    /**
     * Verifies each real raw-slot transaction against cursor and item identity before resync.
     * Every next click waits for a fresh authoritative full inventory snapshot; the
     * client never predicts tagged component changes.
     */
    private void inventoryClick(int index) {
        String[] steps = {"inventory-pickup", "inventory-place", "inventory-pickup-back", "inventory-restore"};
        players.request("alpha", steps[index]);
        players.await("real inventory click " + index, 60, () -> clickedSlots.size() == index + 1, () -> after(2, () -> {
            boolean cursorHasBow = index == 0 || index == 2;
            context.check("inventory_cursor_" + index, true, cursorHasBow
                    ? originalAlpha.getItemOnCursor().equals(managedBow) : originalAlpha.getItemOnCursor().getType().isAir());
            ItemStack inOne = originalAlpha.getInventory().getItem(1), inFour = originalAlpha.getInventory().getItem(4);
            context.check("inventory_identity_and_slots_" + index, true,
                    (index == 3 ? managedBow.equals(inOne) : inOne == null || inOne.getType().isAir())
                    && (index == 1 ? managedBow.equals(inFour) : inFour == null || inFour.getType().isAir()));
            originalAlpha.updateInventory(); // Authoritative resync; the client never predicts tagged component hashes.
            after(5, () -> {
                if (index < 3) inventoryClick(index + 1);
                else {
                    context.check("real_inventory_click_transactions", List.of(37, 40, 40, 37), List.copyOf(clickedSlots));
                    select();
                }
            });
        }));
    }
    /**
     * Requires applied slot, yaw and position changes plus a native swing before advancing.
     */
    private void select() {
        players.request("alpha", "select");
        players.await("selected slot", 50, () -> originalAlpha.getInventory().getHeldItemSlot() == 1, () -> {
            context.check("real_selected_slot", true, count("held") >= 1);
            players.request("alpha", "look");
            players.await("look accepted", 50, () -> Math.abs(originalAlpha.getLocation().getYaw()) < 0.01, () -> {
                players.request("alpha", "move");
                players.await("movement accepted", 50, () -> Math.abs(originalAlpha.getLocation().getZ() - 0.75) < 0.01, () -> {
                    context.check("real_look_and_movement", true, count("move") >= 2);
                    players.request("alpha", "swing");
                    players.await("swing event", 50, () -> count("swing") > 0, this::swap);
                });
            });
        });
    }
    /**
     * Checks both hand swaps and the one-item drop delta, preserving ownership of the dropped entity.
     */
    private void swap() {
        context.check("real_swing_event", true, count("swing") > 0);
        players.request("alpha", "swap");
        players.await("swap hands", 50, () -> originalAlpha.getInventory().getItemInOffHand().getType() == Material.BOW, () -> {
            players.request("alpha", "swap-back");
            players.await("swap back", 50, () -> originalAlpha.getInventory().getItemInMainHand().getType() == Material.BOW, () -> {
                context.check("real_swap_hands_events", 2, count("swap"));
                players.request("alpha", "select-drop");
                players.await("drop slot", 50, () -> originalAlpha.getInventory().getHeldItemSlot() == 2, () -> {
                    players.request("alpha", "drop");
                    players.await("drop event", 50, () -> count("drop") == 1, () -> after(2, () -> {
                        context.check("real_drop_inventory_delta", 1, originalAlpha.getInventory().getItem(2).getAmount());
                        players.request("alpha", "select-bow");
                        players.await("bow reselected", 50, () -> originalAlpha.getInventory().getHeldItemSlot() == 1, this::block);
                    }));
                });
            });
        });
    }
    /**
     * Requires the lever's native state change before a deliberately vetoed melee hit.
     */
    private void block() {
        players.request("alpha", "block");
        players.await("block interaction accepted", 60, () -> ((Switch) lever.getBlockData()).isPowered(), () -> {
            context.check("real_block_interaction_effect", true, count("block") > 0);
            healthBefore = melee.getHealth();
            damageHealthBefore.add(healthBefore);
            players.request("alpha", "cancelled-hit");
            players.await("cancelled damage dispatch", 60, () -> !damage.events().isEmpty(), () -> after(2, () -> {
                var event = damage.events().getFirst();
                context.check("real_cancelled_damage_observed", true, event.cancelled() && event.player().equals(players.identity("alpha")));
                context.check("cancelled_damage_preserves_health", healthBefore, melee.getHealth());
                damageHealthAfter.add(melee.getHealth());
                cancelDamage = false;
                after(25, this::accepted);
            }));
        });
    }
    /**
     * Separates two players' accepted melee hits by cooldown, then requests real bow draw/release.
     * Health deltas are checked against native final damage before the target is removed.
     */
    private void accepted() {
        healthBefore = melee.getHealth();
        damageHealthBefore.add(healthBefore);
        players.request("alpha", "accepted-hit");
        players.await("accepted damage dispatch", 60, () -> damage.events().size() >= 2, () -> after(2, () -> {
            var row = damage.events().get(1);
            context.check("accepted_damage_health_matches_final_event", true, !row.cancelled() && row.finalDamage() > 0
                    && Math.abs(healthBefore - melee.getHealth() - row.finalDamage()) < 1e-8);
            damageHealthAfter.add(melee.getHealth());
            after(25, () -> {
                double betaHealthBefore = melee.getHealth();
                damageHealthBefore.add(betaHealthBefore);
                players.request("beta", "attack");
                players.await("second player damage", 60, () -> damage.events(players.identity("beta")).size() == 1, () -> after(2, () -> {
                    var beta = damage.events(players.identity("beta")).getFirst();
                    context.check("two_player_damage_attribution", true, damage.events(players.identity("alpha")).size() == 2
                            && beta.directDamager().equals(players.identity("beta")) && beta.target().equals(melee.getUniqueId())
                            && beta.cause().equals("ENTITY_ATTACK") && !beta.cancelled() && beta.finalDamage() > 0
                            && Math.abs(betaHealthBefore - melee.getHealth() - beta.finalDamage()) < 1e-8);
                    damageHealthAfter.add(melee.getHealth());
                    melee.remove();
                    arrowHealthBefore = arrowTarget.getHealth();
                    damageHealthBefore.add(arrowHealthBefore);
                    players.request("alpha", "draw");
                    players.await("real bow draw", 60, originalAlpha::isHandRaised, () -> after(25, () -> {
                        players.request("alpha", "release");
                        players.await("native player arrow damage", 100,
                                () -> damage.events().stream().anyMatch(impact -> impact.cause().equals("PROJECTILE")), this::shot);
                    }));
                }));
            });
        }));
    }
    /**
     * Binds arrow damage to the observed release UUID, then uses API death and packet respawn.
     * The death setup is explicit; only the subsequent respawn/reconnect actions are packet claims.
     */
    private void shot() {
        after(2, () -> {
            var row = damage.events().stream().filter(event -> event.cause().equals("PROJECTILE")).findFirst().orElseThrow();
            context.check("real_bow_release_and_projectile_damage", true, count("shoot") == 1 && !row.cancelled()
                    && row.player().equals(players.identity("alpha")) && row.directDamager().equals(releasedArrow)
                    && row.target().equals(arrowTarget.getUniqueId()) && row.finalDamage() > 0);
            context.check("projectile_damage_health_matches_event", true,
                    Math.abs(row.healthAtDispatch() - arrowHealthBefore) < 1e-8
                    && Math.abs(arrowHealthBefore - arrowTarget.getHealth()
                            - Math.min(arrowHealthBefore, row.finalDamage())) < 1e-8);
            damageHealthAfter.add(arrowTarget.getHealth());
            checkDamageProbe();
            context.observe("deathSetup", "Server public API sets health to zero to test real death dispatch and packet-driven respawn; not a client damage claim.");
            originalAlpha.setHealth(0);
            players.await("real player death", 60, () -> count("death") == 1, () -> after(3, () -> {
                players.request("alpha", "respawn");
                players.await("packet respawn accepted", 100, () -> count("respawn") == 1 && !originalAlpha.isDead(), () -> {
                    context.check("real_death_and_packet_respawn", true, originalAlpha.getHealth() > 0);
                    players.request("alpha", "reconnect");
                    players.await("second connection generation", 150, () -> players.joins("alpha") == 2 && players.allOnline(), this::reconnected);
                });
            }));
        });
    }
    /**
     * Cross-checks all four ordered native events, source UUIDs and next-tick health cohorts.
     * Independent before/after HP samples catch a probe that confuses dispatch with application.
     */
    private void checkDamageProbe() {
        var rows = damage.events();
        var cohorts = damage.healthCohorts();
        context.check("damage_probe_event_sequence", List.of(1L, 2L, 3L, 4L), rows.stream().map(DamageObservationProbe.Damage::sequence).toList());
        context.check("damage_probe_cohort_membership", List.of(List.of(1L), List.of(2L), List.of(3L), List.of(4L)),
                cohorts.stream().map(DamageObservationProbe.HealthCohort::eventSequences).toList());
        context.check("damage_probe_dispatch_health", List.copyOf(damageHealthBefore),
                rows.stream().map(DamageObservationProbe.Damage::healthAtDispatch).toList());
        context.check("damage_probe_observed_health", List.copyOf(damageHealthAfter),
                cohorts.stream().map(DamageObservationProbe.HealthCohort::health).toList());
        List<UUID> expectedTargets = List.of(melee.getUniqueId(), melee.getUniqueId(), melee.getUniqueId(), arrowTarget.getUniqueId());
        List<UUID> expectedPlayers = List.of(players.identity("alpha"), players.identity("alpha"), players.identity("beta"), players.identity("alpha"));
        List<UUID> expectedDirect = List.of(players.identity("alpha"), players.identity("alpha"), players.identity("beta"), releasedArrow);
        boolean fields = rows.size() == 4 && cohorts.size() == 4;
        for (int i = 0; fields && i < 4; i++) {
            var row = rows.get(i); var cohort = cohorts.get(i);
            fields = row.target().equals(expectedTargets.get(i)) && row.player().equals(expectedPlayers.get(i))
                    && row.directDamager().equals(expectedDirect.get(i)) && row.cause().equals(i == 3 ? "PROJECTILE" : "ENTITY_ATTACK")
                    && row.cancelled() == (i == 0) && row.initialDamage() > 0
                    && (i == 0 ? row.initialDamage() != CANCELLED_FIXTURE_DAMAGE && row.settledDamage() == CANCELLED_FIXTURE_DAMAGE
                                    && row.finalDamage() == CANCELLED_FIXTURE_DAMAGE
                               : row.initialDamage() == row.settledDamage() && row.settledDamage() == row.finalDamage())
                    && cohort.target().equals(expectedTargets.get(i)) && cohort.dispatchTick() == row.tick()
                    && cohort.observedTick() > row.tick() && cohort.dead() == (damageHealthAfter.get(i) == 0.0)
                    && (i == 0 || rows.get(i - 1).tick() < row.tick());
        }
        context.check("damage_probe_fields_and_tick_boundaries", true, fields);
    }
    /**
     * Requires a fresh Player object for the same UUID, then verifies both sessions quit and caches clear.
     */
    private void reconnected() {
        context.check("reconnect_same_uuid_new_player", true, players.player("alpha") != originalAlpha
                && players.player("alpha").getUniqueId().equals(originalAlpha.getUniqueId()) && players.quits("alpha") == 1);
        after(15, () -> command("status", () -> {
            players.request("beta", "quit");
            players.await("beta disconnected", 60, () -> players.quits("beta") == 1, () -> {
                players.request("alpha", "quit");
                players.await("all actors disconnected", 60, () -> players.quits("alpha") == 2, () -> {
                    context.check("all_actor_sessions_cleanly_quit", true, Bukkit.getOnlinePlayers().isEmpty());
                    context.check("production_sessions_cleared", 0, context.production().equipment().sessionCount());
                    context.finish();
                });
            });
        }));
    }
    /**
     * Schedules the next stage through the context's failure and cancellation owner.
     */
    private void after(int ticks, ScenarioContext.Step step) { context.later(ticks, step); }
    /**
     * Reads a native-event counter, returning zero before its first observation.
     */
    private int count(String kind) { return events.getOrDefault(kind, 0); }
    /**
     * Increments the matching native-event counter without advancing the client plan.
     */
    private void record(String kind) { events.merge(kind, 1, Integer::sum); }
    /**
     * Filters callbacks by the declared alpha UUID across reconnect.
     */
    private boolean alpha(Player player) { return player.getUniqueId().equals(players.identity("alpha")); }
    /**
     * Counts alpha's actual command dispatch; message receipts separately prove command output.
     * @param e native command event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent e) { if (alpha(e.getPlayer())) record("command"); }
    /**
     * Counts accepted native hotbar changes.
     * @param e native selection event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void held(PlayerItemHeldEvent e) { if (alpha(e.getPlayer())) record("held"); }
    /**
     * Records actual raw slots and rejects a button different from the declared left-click plan.
     * @param e native inventory transaction event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void inventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player && alpha(player)) {
            if (!e.isLeftClick()) { context.fail(new IllegalStateException("Unexpected inventory click button")); return; }
            clickedSlots.add(e.getRawSlot());
        }
    }
    /**
     * Counts actual movement/look callbacks for alpha.
     * @param e native movement event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void move(PlayerMoveEvent e) { if (alpha(e.getPlayer())) record("move"); }
    /**
     * Counts actual arm-animation callbacks.
     * @param e native animation event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void swing(PlayerAnimationEvent e) { if (alpha(e.getPlayer())) record("swing"); }
    /**
     * Counts accepted native hand-swap callbacks.
     * @param e native hand-swap event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e) { if (alpha(e.getPlayer())) record("swap"); }
    /**
     * Owns each observed dropped item so the primitive test cannot leak entities.
     * @param e native item-drop event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void drop(PlayerDropItemEvent e) { if (alpha(e.getPlayer())) { record("drop"); context.own(e.getItemDrop()); } }
    /**
     * Counts interactions with the exact fixture lever, independent of its later powered-state check.
     * @param e native block interaction
     */
    @EventHandler(priority=EventPriority.MONITOR) public void block(PlayerInteractEvent e) { if (alpha(e.getPlayer()) && e.getClickedBlock() != null && e.getClickedBlock().equals(lever)) record("block"); }
    /**
     * Injects the explicit first-melee veto and diagnostic damage override only for the fixture target.
     * @param e real native damage event, not a synthesized dispatch
     */
    @EventHandler(priority=EventPriority.HIGH) public void cancel(EntityDamageByEntityEvent e) {
        if (cancelDamage && e.getEntity().equals(melee)) {
            e.setDamage(CANCELLED_FIXTURE_DAMAGE); // Test-only modifier proves LOWEST/MONITOR capture is distinct.
            e.setCancelled(true);
        }
    }
    /**
     * Captures actual released projectile identity and registers its removal ownership.
     * @param e native bow release event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void shoot(EntityShootBowEvent e) { if (e.getEntity() instanceof Player p && alpha(p)) { record("shoot"); releasedArrow = e.getProjectile().getUniqueId(); context.own(e.getProjectile()); } }
    /**
     * Observes the API-induced death while suppressing fixture drops/XP contamination.
     * @param e native death event
     */
    @EventHandler(priority=EventPriority.HIGH) public void death(PlayerDeathEvent e) { if (alpha(e.getEntity())) { record("death"); e.getDrops().clear(); e.setDroppedExp(0); } }
    /**
     * Counts the native callback produced by the requested respawn packet.
     * @param e native respawn event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e) { if (alpha(e.getPlayer())) record("respawn"); }
}
