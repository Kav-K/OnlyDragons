package com.kaveenk.onlydragons.gametests.fixtures;

import com.kaveenk.onlydragons.gametests.*;
import java.nio.file.Files;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Generic starter-config calibration, not managed-encounter persistence evidence. */
public final class RestartScenario implements Scenario, Listener {
    private final boolean abort;
    private final Map<String, Integer> commands = new HashMap<>();
    private ScenarioContext c;
    private PlayerFixture players;
    private RestartPhase phase;
    private Cow owned;
    private org.bukkit.block.Block block;
    private org.bukkit.block.data.BlockData original;
    public RestartScenario(boolean abort) { this.abort = abort; }

    @Override public void start(ScenarioContext context) throws Exception {
        c = context;
        phase = Objects.requireNonNull(c.restartPhase(), "Restart runner context required");
        c.mechanicRevision("starter-restart-v1");
        c.check("server_thread", true, Bukkit.isPrimaryThread());
        c.check("production_enabled", true, c.production().isEnabled());
        c.observe("restart", Map.of("parentRunId", phase.parentRunId(), "index", phase.index(), "nonce", phase.nonce()));
        var world = Bukkit.getWorlds().getFirst();
        c.observe("configOnBootSha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                Files.readAllBytes(c.production().getDataFolder().toPath().resolve("config.yml")))));
        c.observe("restartWorld", Map.of("uuid", world.getUID().toString(), "name", world.getName()));
        c.observe("scope", "Generic starter configuration calibration only. Saved configuration is server setup; commands are real packets; greeting is the production service observation.");
        String expected = phase.index() == 1 ? "Welcome, probe! Your development plugin is running." : greeting("probe");
        c.check("production_config_on_boot", expected, c.production().greetings().welcome("probe"));
        if (phase.index() == 2) {
            c.check("previous_phase_available", true, phase.previousReport().contains(phase.previousNonce()));
            byte[] initial = Files.readAllBytes(phase.initialConfigPath());
            c.cleanup("initial-config", () -> {
                try {
                    Files.write(c.production().getDataFolder().toPath().resolve("config.yml"), initial);
                    c.production().reloadSettings();
                    c.check("initial_config_restored", true, Arrays.equals(initial,
                            Files.readAllBytes(c.production().getDataFolder().toPath().resolve("config.yml"))));
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
        }
        players = new PlayerFixture(c);
        c.listen(this);
        players.await("two restart actors", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    private String greeting(String name) { return "Restart calibration " + phase.parentRunId() + " hello " + name; }
    private void setup() {
        var alpha = players.player("alpha"); var beta = players.player("beta");
        c.check("two_distinct_real_players", true, !alpha.getUniqueId().equals(beta.getUniqueId()) && Bukkit.getOnlinePlayers().size() == 2);
        c.check("loopback_nonop_players", true, List.of(alpha, beta).stream().allMatch(p -> !p.isOp()
                && p.getAddress() != null && p.getAddress().getAddress().isLoopbackAddress()));
        players.permission("alpha", "onlydragons.admin", true);
        players.permission("beta", "onlydragons.admin", false);
        c.cleanup("permission-check", () -> c.check("permissions_removed", true,
                !alpha.hasPermission("onlydragons.admin") && !beta.hasPermission("onlydragons.admin")));
        block = alpha.getWorld().getBlockAt(4, 100, 4); original = block.getBlockData().clone();
        c.cleanup("block", () -> { block.setBlockData(original, false); c.check("block_restored", original.getAsString(), block.getBlockData().getAsString()); });
        block.setType(original.getMaterial() == Material.STONE ? Material.DIRT : Material.STONE, false);
        c.tickChunk(block.getChunk());
        owned = c.own(alpha.getWorld().spawn(block.getLocation().add(.5, 2, .5), Cow.class));
        owned.setAI(false); owned.setGravity(false);
        c.later(1000, () -> { throw new AssertionError("Leaked restart callback"); });
        if (phase.index() == 1) {
            c.production().getConfig().set("welcome-message", greeting("{player}"));
            c.production().saveConfig();
            c.observe("configurationSetup", "Production saveConfig after explicit fixture configuration edit; reload is requested from the client.");
        }
        if (phase.index() == 2) { afterReload(); return; }
        players.request("alpha", "reload"); players.request("beta", "reload");
        players.await("real reload commands", 100, () -> commands.getOrDefault("reload", 0) == 2,
                () -> c.later(2, this::afterReload));
    }
    private void afterReload() {
        if (phase.index() == 1) c.check("real_reload_commands", 2, commands.get("reload"));
        c.check("production_reloaded_saved_config", greeting("probe"), c.production().greetings().welcome("probe"));
        players.request("alpha", "status"); players.request("beta", "status");
        players.await("real status commands", 100, () -> commands.getOrDefault("status", 0) == 2,
                () -> c.later(2, this::complete));
    }
    private void complete() {
        c.check("real_status_commands", 2, commands.get("status"));
        c.check("resources_live_before_completion", true, owned.isValid() && !block.getBlockData().equals(original)
                && players.allOnline() && players.player("alpha").hasPermission("onlydragons.admin"));
        if (abort && phase.index() == 2) { c.abort(); return; }
        players.request("alpha", "quit"); players.request("beta", "quit");
        players.await("both restart actors quit", 100, () -> players.quits("alpha") == 1 && players.quits("beta") == 1,
                () -> { c.check("both_actors_quit", true, !players.allOnline() && Bukkit.getOnlinePlayers().isEmpty()); c.finish(); });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        if (!Set.of(players.identity("alpha"), players.identity("beta")).contains(event.getPlayer().getUniqueId())) return;
        for (String action : List.of("reload", "status"))
            if (event.getMessage().equals("/onlydragons " + action)) commands.merge(action, 1, Integer::sum);
    }
}
