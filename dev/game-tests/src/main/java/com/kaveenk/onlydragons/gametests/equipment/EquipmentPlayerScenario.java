package com.kaveenk.onlydragons.gametests.equipment;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Real single-player equipment listener calibration using legacy protocol actions.
 * Login/select/draw/release/quit are packets. Commands, item edits, death and respawn
 * are explicitly public server-API setup, so their outcomes must not be relabeled as
 * packet transactions. Immutable old snapshots and session cleanup are independent
 * of the native arrow's observed flight.
 */
public final class EquipmentPlayerScenario implements Scenario {
    /**
     * Admits the legacy disposable actor mode and installs an owned bounded flow.
     * @param context server-thread report and cleanup owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("equipment-player-v2");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("disposable_protocol_mode", "protocol-calibration", System.getProperty("onlydragons.test.playerMode", ""));
        context.check("offline_fixture", false, Bukkit.getOnlineMode());
        context.check("equipment_service_loaded_from_production", true, context.production().equipment() != null
                && EquipmentStatsService.class.getClassLoader() == context.production().getClass().getClassLoader());
        context.observe("actor_scope", "Offline loopback protocol login/select/draw/release/quit; real Paper Player and production listeners. Commands, inventory edits, death and respawn use public server APIs; no human input/authentication/visual claim.");
        context.listen(new Flow(context));
        context.later(900, () -> context.fail(new IllegalStateException("Equipment player fixture timed out")));
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }

    /**
     * One connection's server-thread progression through equipment edits, death and quit.
     * Event booleans prevent duplicate stage advancement; cached inspections must be
     * produced by actual production listeners before the next assertion.
     */
    private static final class Flow implements Listener {
        private final ScenarioContext c;
        private final EquipmentStatsService stats;
        private final String name;
        private final UUID id;
        private final WeaponItemCodec codec = new WeaponItemCodec(CalibrationLoadouts.registry());
        private Player player;
        private boolean selected, drawing, shot, killed, respawned, quitting;
        private UUID weaponId;
        private EquipmentStatsService.Inspection beforeEdit;

        /**
         * Captures the production service and exact runner-derived actor UUID.
         */
        Flow(ScenarioContext context) {
            c = context; stats = c.production().equipment();
            name = "od_" + c.harness().runId().substring(0, 13);
            id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
        /**
         * Filters native callbacks by the bound actor UUID.
         */
        private boolean ours(Player candidate) { return candidate.getUniqueId().equals(id); }
        /**
         * Requests a legacy protocol action using this boot's nonce.
         */
        private void request(String action) { player.sendMessage(Component.text("OD_PLAYER:" + c.harness().runId() + ":" + action)); }
        /**
         * Converts synchronous event-stage exceptions into the context's failed cleanup path.
         */
        private void guarded(ScenarioContext.Step action) {
            try { action.run(); } catch (Exception | AssertionError failure) { c.fail(failure); }
        }
        /**
         * Requires the production listener cache to exist; it never refreshes it to manufacture readiness.
         */
        private EquipmentStatsService.Inspection cached() {
            var value = stats.cached(id);
            if (value == null) throw new IllegalStateException("Production equipment listener has not populated the cache");
            return value;
        }
        /**
         * Reads the observed cached snapshot's raw stat before profile caps.
         */
        private double raw(StatKey key) { return cached().stats().snapshot().raw(key); }
        /**
         * Rejects any inventory item that the real managed-item codec does not validate.
         */
        private ItemReadResult.Valid valid(ItemStack stack) {
            if (codec.decode(stack) instanceof ItemReadResult.Valid value) return value;
            throw new IllegalStateException("Expected a production-validated managed bow");
        }
        /**
         * Scopes a calibration permission to setup commands and verifies its removal in finally.
         */
        private void permitted(ScenarioContext.Step commands) throws Exception {
            var attachment = player.addAttachment(c.harness(), "onlydragons.calibration", true);
            try { commands.run(); }
            finally {
                attachment.remove();
                if (player.getEffectivePermissions().stream().anyMatch(info -> info.getAttachment() == attachment)) {
                    throw new IllegalStateException("Fixture permission attachment was retained");
                }
            }
        }

        /**
         * Checks real identity/loopback/non-op status before delayed setup.
         * @param event native join callback
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void join(PlayerJoinEvent event) {
            if (!ours(event.getPlayer())) return;
            guarded(() -> {
                player = event.getPlayer();
                c.check("real_player_join", true, player.isOnline() && player.getName().equals(name));
                c.check("player_uuid", id.toString(), player.getUniqueId().toString());
                c.check("player_loopback", true, player.getAddress() != null && player.getAddress().getAddress().isLoopbackAddress());
                c.check("player_not_op", false, player.isOp());
                c.later(2, this::prepare);
            });
        }
        /**
         * Uses explicit API commands/inventory setup, then requests a real hotbar selection.
         */
        private void prepare() throws Exception {
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(true); player.setFlying(true);
            player.teleport(new Location(player.getWorld(), 0.5, 100, 0.5, 0, 0));
            player.getInventory().clear(); player.getInventory().setHeldItemSlot(0);
            c.check("nonop_calibration_denied", false, player.hasPermission("onlydragons.calibration"));
            player.performCommand("onlydragons dev loadout ordinary");
            c.check("denied_player_grant_has_no_item", true, player.getInventory().isEmpty());
            permitted(() -> {
                player.performCommand("onlydragons dev loadout ordinary");
                player.performCommand("onlydragons dev loadout ferocity_500");
                var ordinary = valid(player.getInventory().getItem(0));
                var capped = valid(player.getInventory().getItem(1));
                c.check("permitted_player_grants", List.of("ordinary", "ferocity_500"), List.of(
                        ordinary.item().instance().identity().definitionId(), capped.item().instance().identity().definitionId()));
                c.check("grants_have_distinct_identity", true, !ordinary.item().instance().identity().instanceId()
                        .equals(capped.item().instance().identity().instanceId()));
            });
            c.check("grant_permission_removed", false, player.hasPermission("onlydragons.calibration"));
            var bow = player.getInventory().getItem(0);
            var offhand = player.getInventory().getItem(1);
            weaponId = valid(bow).item().instance().identity().instanceId();
            player.getInventory().clear(); player.getInventory().setItem(1, bow);
            player.getInventory().setItemInOffHand(offhand);
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 8));
            c.later(10, () -> {
                c.check("inventory_event_unequipped_baseline", List.of(0.0, 0.0), List.of(raw(StatKey.WEAPON_DAMAGE), raw(StatKey.FEROCITY)));
                request("select");
            });
        }

        /**
         * Waits for the production held-slot refresh before editing the same item UUID.
         * @param event native selection callback
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void held(PlayerItemHeldEvent event) {
            if (!ours(event.getPlayer()) || selected || event.getNewSlot() != 1) return;
            guarded(() -> {
                selected = true;
                c.check("real_held_slot_event", List.of(0, 1), List.of(event.getPreviousSlot(), event.getNewSlot()));
                c.later(2, () -> {
                    beforeEdit = cached();
                    c.check("held_listener_refresh", List.of(100.0, 50.0, 0.0), List.of(raw(StatKey.WEAPON_DAMAGE), raw(StatKey.CRIT_DAMAGE), raw(StatKey.FEROCITY)));
                    var held = player.getInventory().getItemInMainHand();
                    var input = valid(held).item().instance();
                    var edited = codec.encode(CalibrationLoadouts.registry().edit(input, Map.of("vicious", 3), List.of()));
                    held.setItemMeta(edited.getItemMeta());
                    player.getInventory().setItemInMainHand(held);
                    c.later(2, this::afterEdit);
                });
            });
        }
        /**
         * Checks immutable old stats, edited fingerprint and invalid-stack quarantine/recovery.
         */
        private void afterEdit() {
            c.check("same_uuid_item_edit", weaponId.toString(), valid(player.getInventory().getItemInMainHand()).item().instance().identity().instanceId().toString());
            c.check("item_edit_listener_refresh", 3.0, raw(StatKey.FEROCITY));
            c.check("offhand_does_not_stack", 100.0, raw(StatKey.WEAPON_DAMAGE));
            c.check("old_equipment_snapshot_immutable", 0.0, beforeEdit.stats().snapshot().raw(StatKey.FEROCITY));
            c.check("item_edit_changes_revision", true, !beforeEdit.stats().snapshot().revision().equals(cached().stats().snapshot().revision()));
            var invalid = player.getInventory().getItemInMainHand(); invalid.setAmount(2);
            player.getInventory().setItemInMainHand(invalid);
            c.later(2, () -> {
                c.check("invalid_item_listener_quarantine", true, cached().fingerprint().mainHand() instanceof ItemReadResult.Invalid
                        && raw(StatKey.WEAPON_DAMAGE) == 0 && raw(StatKey.FEROCITY) == 0);
                var restored = player.getInventory().getItemInMainHand(); restored.setAmount(1);
                player.getInventory().setItemInMainHand(restored);
                c.later(2, this::commandsAndDraw);
            });
        }
        /**
         * Checks public-API command/bonus replacement behavior before the packet-driven bow trial.
         */
        private void commandsAndDraw() throws Exception {
            c.check("restored_item_listener_refresh", List.of(100.0, 3.0), List.of(raw(StatKey.WEAPON_DAMAGE), raw(StatKey.FEROCITY)));
            var before = cached();
            c.check("real_player_stats_command", true, player.performCommand("onlydragons stats explain"));
            c.check("stats_command_reuses_current_fingerprint", true, before == cached());
            permitted(() -> {
                player.performCommand("onlydragons dev bonus ferocity 25");
                c.check("player_bonus_applied", 28.0, raw(StatKey.FEROCITY));
                player.performCommand("onlydragons dev bonus ferocity 10");
                c.check("player_bonus_replaced", 13.0, raw(StatKey.FEROCITY));
                var validBonus = cached();
                player.performCommand("onlydragons dev bonus ferocity NaN");
                c.check("invalid_player_bonus_atomic", true, validBonus == cached() && raw(StatKey.FEROCITY) == 13);
            });
            player.performCommand("onlydragons dev clear");
            c.check("denied_player_clear_preserves_bonus", 13.0, raw(StatKey.FEROCITY));
            c.check("bonus_permission_removed", false, player.hasPermission("onlydragons.calibration"));
            request("draw");
        }

        /**
         * Begins draw polling after a real main-hand bow-use callback.
         * @param event native interaction
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void interact(PlayerInteractEvent event) {
            if (!ours(event.getPlayer()) || drawing || !selected || event.getHand() != EquipmentSlot.HAND
                    || !event.getAction().isRightClick() || event.getMaterial() != Material.BOW) return;
            guarded(() -> { drawing = true; awaitDraw(0); });
        }
        /**
         * Requires observed native hand-raised state before the full draw delay and release request.
         */
        private void awaitDraw(int elapsed) {
            c.later(1, () -> {
                if (player.isHandRaised()) {
                    c.check("real_bow_draw", true, player.isHandRaised());
                    c.later(25, () -> request("release"));
                } else if (elapsed >= 20) throw new IllegalStateException("Paper did not begin bow draw");
                else awaitDraw(elapsed + 1);
            });
        }
        /**
         * Owns the native released arrow and later induces API death to check production session cleanup.
         * @param event real uncancelled bow release
         */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void shoot(EntityShootBowEvent event) {
            if (!(event.getEntity() instanceof Player owner) || !ours(owner) || shot) return;
            guarded(() -> {
                shot = true;
                if (!(event.getProjectile() instanceof Arrow arrow)) throw new IllegalStateException("Expected native Arrow");
                c.own(arrow);
                c.check("real_managed_bow_release", true, drawing && event.getForce() >= 0.99f
                        && arrow.getShooter() instanceof Player shooter && ours(shooter));
                c.observe("arrowUuid", arrow.getUniqueId().toString());
                c.later(2, () -> {
                    c.check("native_owned_arrow_flight", true, arrow.isValid() && arrow.getVelocity().lengthSquared() > 0);
                    killed = true;
                    player.setHealth(0); // Actual Paper death; never call/manufacture PlayerDeathEvent.
                    c.later(2, () -> {
                        c.check("death_listener_clears_session", true, player.isDead() && stats.cached(id) == null);
                        player.spigot().respawn(); // Public API fixture, not a protocol respawn-input claim.
                    });
                });
            });
        }
        /**
         * Keeps fixture inventory while removing drops/XP from the deliberate death trial.
         * @param event native death callback from API health change
         */
        @EventHandler(priority = EventPriority.HIGHEST)
        public void death(PlayerDeathEvent event) {
            if (!ours(event.getEntity())) return;
            guarded(() -> {
                c.check("real_player_death_event", true, killed && shot);
                event.setKeepInventory(true); event.getDrops().clear(); event.setDroppedExp(0);
            });
        }
        /**
         * Checks session rebuilding after public-API respawn, without retaining the previous bonus.
         * @param event native respawn callback
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void respawn(PlayerRespawnEvent event) {
            if (!ours(event.getPlayer()) || respawned) return;
            guarded(() -> {
                respawned = true;
                c.check("real_player_respawn_event", true, killed);
                c.later(3, () -> {
                    c.check("respawn_listener_rebuilds_without_bonus", List.of(100.0, 3.0), List.of(raw(StatKey.WEAPON_DAMAGE), raw(StatKey.FEROCITY)));
                    permitted(() -> player.performCommand("onlydragons dev bonus ferocity 9"));
                    c.check("new_session_bonus_before_quit", 12.0, raw(StatKey.FEROCITY));
                    c.check("all_fixture_permissions_removed", false, player.hasPermission("onlydragons.calibration"));
                    quitting = true; request("quit");
                });
            });
        }
        /**
         * Verifies the requested quit removes both the real player and production equipment session.
         * @param event native quit callback
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void quit(PlayerQuitEvent event) {
            if (!ours(event.getPlayer())) return;
            guarded(() -> {
                c.check("real_player_quit", true, quitting && respawned);
                c.later(3, () -> {
                    c.check("quit_listener_clears_session", true, stats.cached(id) == null && stats.sessionCount() == 0);
                    c.check("player_removed_after_quit", true, Bukkit.getPlayer(id) == null);
                    c.finish();
                });
            });
        }
    }
}
