package com.kaveenk.onlydragons.gametests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class GameTestsPlugin extends JavaPlugin {
    private final Map<String, Scenario> scenarios = Map.ofEntries(
            Map.entry("owned-firing", new com.kaveenk.onlydragons.gametests.projectile.OwnedFiringScenario()),
            Map.entry("dragon-definitions-abort", new com.kaveenk.onlydragons.gametests.definition.DragonDefinitionScenario(true)),
            Map.entry("dragon-definitions", new com.kaveenk.onlydragons.gametests.definition.DragonDefinitionScenario()),
            Map.entry("headless-player-primitives", new com.kaveenk.onlydragons.gametests.fixtures.PlayerPrimitivesScenario()),
            Map.entry("headless-player-cleanup-abort", new com.kaveenk.onlydragons.gametests.fixtures.PlayerCleanupAbortScenario()),
            Map.entry("equipment-player", new com.kaveenk.onlydragons.gametests.equipment.EquipmentPlayerScenario()),
            Map.entry("equipment-stats", new com.kaveenk.onlydragons.gametests.equipment.EquipmentStatsScenario()),
            Map.entry("lifecycle-calibration", new CalibrationScenario(false)),
            Map.entry("deliberate-failure", new CalibrationScenario(true)),
            Map.entry("foundation-contracts", new ContractScenario()),
            Map.entry("protocol-player-calibration", new PlayerCalibrationScenario()),
            Map.entry("protocol-player-soak", new PlayerCalibrationScenario(true)),
            Map.entry("item-identity", new com.kaveenk.onlydragons.gametests.item.ItemIdentityScenario()),
            Map.entry("projectile-player-feasibility", new com.kaveenk.onlydragons.gametests.projectile.ProjectilePlayerFeasibilityScenario()),
            Map.entry("projectile-feasibility", new com.kaveenk.onlydragons.gametests.projectile.ProjectileFeasibilityScenario()),
            Map.entry("projectile-cleanup-failure", new com.kaveenk.onlydragons.gametests.projectile.ProjectileCleanupFailureScenario()),
            Map.entry("projectile-cleanup-abort", new com.kaveenk.onlydragons.gametests.projectile.ProjectileCleanupFailureScenario(true)),
            Map.entry("stats-resolution", new com.kaveenk.onlydragons.gametests.stats.StatsResolutionScenario()),
            Map.entry("combat-accounting", new com.kaveenk.onlydragons.gametests.combat.CombatAccountingScenario()),
            Map.entry("enchants-procs", new com.kaveenk.onlydragons.gametests.enchant.EnchantProcScenario()));
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private ScenarioContext active;
    private String runId;
    public String runId() { return runId; }

    @Override public void onEnable() {
        runId = System.getProperty("onlydragons.test.runId", "");
        if (!runId.matches("[a-f0-9]{32}")) throw new IllegalStateException("Only start this test companion through the isolated agent runner");
        Objects.requireNonNull(getCommand("odgametest")).setExecutor(this);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length != 2 || !args[0].equals(runId) || active != null) {
            sender.sendMessage("OD_GAME_TEST_REJECTED invalid console request or scenario already started");
            return true;
        }
        Scenario scenario = scenarios.get(args[1]);
        if (scenario == null) {
            sender.sendMessage("OD_GAME_TEST_REJECTED unknown scenario");
            return true;
        }
        active = new ScenarioContext(this, args[1]);
        try { scenario.start(active); } catch (Exception | AssertionError failure) { active.fail(failure); }
        return true;
    }
    void publish(Map<String, Object> report) {
        // Freeze every server-owned value before handing only text to the I/O executor.
        String payload = Json.write(report);
        writer.submit(() -> {
            try {
                var directory = getDataFolder().toPath();
                Files.createDirectories(directory);
                var temporary = directory.resolve("report.json.tmp");
                Files.writeString(temporary, payload + "\n", StandardCharsets.UTF_8);
                Files.move(temporary, directory.resolve("report.json"), StandardCopyOption.ATOMIC_MOVE);
                getLogger().info("OD_GAME_TEST_COMPLETE " + runId);
            } catch (IOException failure) {
                getLogger().severe("OD_GAME_TEST_REPORT_ERROR " + failure);
            }
        });
    }
    @Override public void onDisable() {
        if (active != null) active.abort();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                getLogger().severe("OD_GAME_TEST_REPORT_ERROR writer did not terminate");
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); writer.shutdownNow(); }
    }
}
