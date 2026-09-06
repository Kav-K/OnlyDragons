package com.kaveenk.onlydragons.gametests;

import java.nio.charset.StandardCharsets;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Validates a real loopback protocol player; no manufactured Bukkit callbacks. */
final class PlayerCalibrationScenario implements Scenario {
    private final boolean soak;

    PlayerCalibrationScenario() { this(false); }
    PlayerCalibrationScenario(boolean soak) { this.soak = soak; }

    @Override public void start(ScenarioContext context) {
        context.mechanicRevision(soak ? "protocol-player-soak-v1" : "protocol-player-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("disposable_protocol_mode", "protocol-calibration", System.getProperty("onlydragons.test.playerMode", ""));
        context.check("offline_fixture", false, Bukkit.getOnlineMode());
        context.observe("authentication", "offline-disposable-loopback");
        context.observe("scope", "Protocol input and real Paper events; no human visuals or authenticated multiplayer");
        String name = "od_" + context.harness().runId().substring(0, 13);
        UUID expectedId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        context.observe("playerName", name);
        context.observe("expectedPlayerUuid", expectedId.toString());

        context.listen(new Listener() {
            private Player player;
            private boolean selected;
            private boolean drawing;
            private boolean shot;
            private boolean quitting;

            private boolean ours(Player candidate) { return candidate.getName().equals(name); }
            private void request(String action) {
                player.sendMessage(Component.text("OD_PLAYER:" + context.harness().runId() + ":" + action));
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void join(PlayerJoinEvent event) {
                if (!ours(event.getPlayer())) return;
                player = event.getPlayer();
                context.check("real_player_join", true, player.isOnline());
                context.check("player_uuid", expectedId.toString(), player.getUniqueId().toString());
                context.check("player_loopback", true, player.getAddress() != null && player.getAddress().getAddress().isLoopbackAddress());
                context.check("player_not_op", false, player.isOp());
                context.observe("joinedPlayerUuid", player.getUniqueId().toString());
                context.later(2, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    player.teleport(new Location(player.getWorld(), 0.5, 100, 0.5, 0, 0));
                    player.getInventory().clear();
                    player.getInventory().setHeldItemSlot(0);
                    player.getInventory().setItem(1, new ItemStack(Material.BOW));
                    player.getInventory().setItem(9, new ItemStack(Material.ARROW, 8));
                    context.later(10, () -> request("select"));
                });
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void held(PlayerItemHeldEvent event) {
                if (!ours(event.getPlayer()) || selected || event.getNewSlot() != 1) return;
                selected = true;
                context.check("real_selected_slot_event", 0, event.getPreviousSlot());
                context.check("selected_slot_new", 1, event.getNewSlot());
                context.later(1, () -> {
                    context.check("selected_bow_applied", Material.BOW.name(), player.getInventory().getItemInMainHand().getType().name());
                    request("draw");
                });
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void interact(PlayerInteractEvent event) {
                if (!ours(event.getPlayer()) || drawing || !selected || event.getHand() != EquipmentSlot.HAND
                        || !event.getAction().isRightClick() || event.getMaterial() != Material.BOW) return;
                drawing = true;
                context.check("real_bow_use_event", true, Bukkit.isPrimaryThread());
                awaitDraw(0);
            }

            private void awaitDraw(int elapsed) {
                context.later(1, () -> {
                    if (player.isHandRaised()) {
                        context.check("server_observed_bow_draw", true, player.isHandRaised());
                        context.later(25, () -> request("release"));
                    } else if (elapsed >= 20) {
                        context.fail(new IllegalStateException("Paper did not start drawing the bow"));
                    } else awaitDraw(elapsed + 1);
                });
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void shoot(EntityShootBowEvent event) {
                if (!(event.getEntity() instanceof Player shooter) || !ours(shooter) || shot) return;
                shot = true;
                context.check("real_bow_release_event", true, drawing);
                context.check("full_bow_draw_force", true, event.getForce() >= 0.99f);
                context.check("native_arrow_projectile", true, event.getProjectile() instanceof Arrow);
                if (!(event.getProjectile() instanceof Arrow arrow)) {
                    context.fail(new IllegalStateException("Expected a real native arrow"));
                    return;
                }
                context.own(arrow);
                context.check("native_projectile_shooter", true, arrow.getShooter() instanceof Player owner && owner.getUniqueId().equals(expectedId));
                context.observe("arrowUuid", arrow.getUniqueId().toString());
                context.observe("bowForce", event.getForce());
                context.later(2, () -> {
                    context.check("native_projectile_live", true, arrow.isValid());
                    context.check("native_projectile_moving", true, arrow.getVelocity().lengthSquared() > 0);
                    if (soak) awaitSoak(System.nanoTime());
                    else quitNormally();
                });
            }

            private void quitNormally() {
                quitting = true;
                request("quit");
            }

            private void awaitSoak(long started) {
                context.later(20, () -> {
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    if (!player.isOnline()) throw new IllegalStateException("Protocol player disconnected during soak");
                    if (elapsedMs < 40_000) { awaitSoak(started); return; }
                    context.check("player_soak_online", true, player.isOnline() && player.getUniqueId().equals(expectedId));
                    context.check("player_soak_elapsed", true, elapsedMs >= 40_000);
                    context.observe("soakElapsedMs", elapsedMs);
                    quitNormally();
                });
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void quit(PlayerQuitEvent event) {
                if (!ours(event.getPlayer())) return;
                context.check("real_player_quit", true, quitting && shot);
                context.later(2, () -> {
                    context.check("player_removed_after_quit", true, Bukkit.getPlayer(expectedId) == null);
                    context.finish();
                });
            }
        });
        context.later(soak ? 2200 : 900, () -> context.fail(new IllegalStateException("Protocol player calibration timed out")));
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }
}
