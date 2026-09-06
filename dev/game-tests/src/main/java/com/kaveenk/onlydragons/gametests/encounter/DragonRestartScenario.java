package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Two real boots: loaded arena queried before setup; production owns first-boot shutdown cleanup. */
public final class DragonRestartScenario implements Scenario, Listener {
    private final boolean legacy;
    private final boolean animation;
    private ScenarioContext c;
    private PlayerFixture players;
    private DevelopmentDragonService dragons;
    private int commands;
    private final java.util.List<java.util.Map<String,Object>> uiChecks = new java.util.ArrayList<>();
    private byte[] initial;
    private RestartPhase phase;
    public DragonRestartScenario(boolean legacy) { this(legacy, false); }
    public DragonRestartScenario(boolean legacy, boolean animation) { this.legacy = legacy; this.animation = animation; }
    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("dragon-restart-v1"); dragons = c.production().dragons();
        phase = Objects.requireNonNull(c.restartPhase()); var world = Bukkit.getWorlds().getFirst();
        c.observe("restart", Map.of("parentRunId", phase.parentRunId(), "index", phase.index(), "nonce", phase.nonce()));
        c.observe("restartWorld", Map.of("uuid", world.getUID().toString(), "name", world.getName()));
        initial = Files.readAllBytes(config());
        c.observe("configOnBootSha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(initial)));
        c.check("production_idle_on_boot", true, dragons.generation().isEmpty() && c.production().combat().activeCount() == 0);
        c.check("legacy_settings_preserved", legacy ? "Legacy hello probe" : "Welcome, probe! Your development plugin is running.", c.production().greetings().welcome("probe"));
        if (legacy) c.check("legacy_extra_preserved", "keep-me", c.production().getConfig().getString("legacy-extra"));
        if (phase.index() == 1) c.check("loaded_arena_before_setup", true, dragons.arena().isEmpty());
        else {
            var expected = new DevelopmentArena(world.getKey().toString(), 0, 100, 0, 24, "test_dragon");
            c.check("loaded_arena_before_setup", true, expected.equals(dragons.arena().orElseThrow()));
            String previous = phase.previousReport();
            var observations = com.google.gson.JsonParser.parseString(previous).getAsJsonObject().getAsJsonObject("observations");
            UUID old = UUID.fromString(observations.get("oldNative").getAsString());
            var chunk = observations.getAsJsonObject("oldChunks");
            int oldX = chunk.get("x").getAsInt(), oldZ = chunk.get("z").getAsInt();
            // Load the recorded first-boot native chunks before asserting absence.
            for (int x = oldX - 2; x <= oldX + 2; x++) for (int z = oldZ - 2; z <= oldZ + 2; z++) c.tickChunk(world.getChunkAt(x, z));
            c.check("old_native_absent_after_chunk_load", true, world.getEntities().stream().noneMatch(e -> e.getUniqueId().equals(old)) && Bukkit.getEntity(old) == null);
            c.check("selected_definition_readback", observations.get("selectedDefinition").getAsString(), c.production().dragonDefinitions().snapshot().select(expected.type()).toString());
        }
        players = new PlayerFixture(c); c.listen(this);
        players.await("restart actor", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    private Path config() { return c.production().getDataFolder().toPath().resolve("config.yml"); }
    private void setup() {
        uiSample("restart-idle", false);
        c.check("restart_ui_initially_empty", true, c.production().dragonHealth().generation().isEmpty() && c.production().dragonHealth().viewerCount()==0);
        var world = players.player("alpha").getWorld();
        players.setupPosition("alpha", new Location(world, 0, 100, -16));
        if (phase.index() == 1) {
            command("denied", () -> {
                try { c.check("denial_no_config_or_memory_change", true, Arrays.equals(initial, Files.readAllBytes(config())) && dragons.arena().isEmpty()); }
                catch (Exception e) { throw new IllegalStateException(e); }
                players.permission("alpha", "onlydragons.practice", true);
                command("unconfigured", () -> {
                    c.check("unconfigured_spawn_rejected", true, dragons.generation().isEmpty());
                    command("setup", () -> {
                        c.check("production_setup_saved", true, dragons.arena().isPresent());
                        persistenceFailure(() -> command("invalid", () -> { c.check("invalid_keeps_previous", 24.0, dragons.arena().orElseThrow().radius()); command("spawn", this::active); }));
                    });
                });
            });
        } else {
            players.permission("alpha", "onlydragons.practice", true);
            command("status", () -> command("spawn", this::active));
        }
    }
    private void persistenceFailure(Runnable next) {
        try {
            byte[] saved = Files.readAllBytes(config());
            Runnable restore = () -> {
                try { if(Files.isDirectory(config())) { Files.delete(config()); Files.write(config(), saved); } }
                catch(Exception failure) { throw new IllegalStateException(failure); }
            };
            c.cleanup("failed-persistence-fixture", restore);
            Files.delete(config()); Files.createDirectory(config());
            command("save-fails", () -> {
                restore.run();
                c.check("failed_read_keeps_loaded_arena", 24.0, dragons.arena().orElseThrow().radius());
                try { c.check("failed_read_fixture_restores_saved_bytes", true, Arrays.equals(saved, Files.readAllBytes(config()))); }
                catch(Exception failure) { throw new IllegalStateException(failure); }
                next.run();
            });
        } catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    private void active() {
        uiSample("restart-active", true);
        c.check("restart_ui_active", 1, c.production().dragonHealth().viewerCount());
        var v = dragons.view().orElseThrow();
        var nativeDragon = (EnderDragon) Bukkit.getEntity(v.entityId());
        c.check("production_real_dragon_active", true, nativeDragon != null && nativeDragon.isValid() && nativeDragon.getDragonBattle() == null
                && v.state() == ManagedCombatService.State.ACTIVE && v.target().currentHealth() == 1000);
        c.observe("oldNative", v.entityId().toString());
        c.observe("oldChunks", Map.of("x", nativeDragon.getLocation().getBlockX() >> 4, "z", nativeDragon.getLocation().getBlockZ() >> 4));
        c.observe("selectedDefinition", dragons.selection().orElseThrow().toString());
        if (phase.index() == 1) {
            command("status", () -> command("duplicate", () -> {
                c.check("duplicate_preserves_generation", v.encounterId().toString(), dragons.generation().orElseThrow().toString());
                // Do not own/reset the production dragon in fixture cleanup. onDisable must remove it.
                c.check("active_at_first_shutdown", true, nativeDragon.isValid() && c.production().combat().activeCount() == 1);
                if(animation) prepareAnimationShutdown(nativeDragon);
                else if(!legacy) c.later(40,()->{
                    c.check("moving_at_first_shutdown",true,dragons.motion().orElseThrow().state().equals("MOVING")
                            &&dragons.motion().orElseThrow().steps()>20&&nativeDragon.getLocation().distance(dragons.arena().orElseThrow().location())>.1);
                    quit();
                });
                else quit();
            }));
        } else command("reset", () -> {
            c.check("restart_new_spawn_reset_cleanup", true, Bukkit.getEntity(nativeDragon.getUniqueId()) == null && c.production().combat().activeCount() == 0
                    && c.production().bows().continuity().tickets().demandCount() == 0 && c.production().bows().continuity().tickets().reservedCount() == 0);
            command("repeat", () -> { uiSample("restart-reset", false); c.check("restart_ui_reset_empty", true, c.production().dragonHealth().generation().isEmpty() && c.production().dragonHealth().viewerCount()==0); c.check("repeat_reset_no_result", true, c.production().combat().completions().isEmpty()); quit(); });
        });
    }
    private void prepareAnimationShutdown(EnderDragon dragon) {
        var p=players.player("alpha");
        p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
        players.await("restart dragon parts",80,()->dragon.getParts().stream().allMatch(part->part.getLocation().getY()>75),()->{
            var box=dragon.getParts().stream().max(java.util.Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox();
            var aim=box.getCenter();players.setupPosition("alpha",new Location(p.getWorld(),aim.getX(),aim.getY()-p.getEyeHeight(),aim.getZ()-12,0,0));
            p.getInventory().setHeldItemSlot(0);
            players.setupItem("alpha",0,c.production().equipment().createLoadout("ordinary"));
            players.setupItem("alpha",9,new org.bukkit.inventory.ItemStack(Material.ARROW,64));
            c.production().equipment().bonus(p,com.kaveenk.onlydragons.domain.stats.StatKey.WEAPON_DAMAGE,900);
            players.request("alpha","use");players.await("restart draw",60,p::isHandRaised,()->c.later(22,()->{
                players.request("alpha","release");players.await("restart native death animation",120,()->dragon.getDeathAnimationTicks()>0,()->{
                    c.check("animation_owned_at_shutdown",true,Bukkit.getEntity(dragon.getUniqueId())==dragon&&c.production().bows().continuity().tickets().demandCount()>0&&dragons.view().orElseThrow().completion().isPresent()&&c.production().combat().activeCount()==1);
                    c.observe("shutdownAnimationTicks",dragon.getDeathAnimationTicks());quit();
                });
            }));
        });
    }
    private void uiSample(String marker, boolean visible) {
        uiChecks.add(Map.of("actor","alpha","session","s1","marker",marker,"generation",visible?"restart-current":"", "title",visible?"Test Dragon (Calibration)  |  1,000 / 1,000 HP  (100%)":"", "percent",visible?1.0:0.0));
        c.observe("bossBarChecks",java.util.List.copyOf(uiChecks));
        players.player("alpha").sendMessage(net.kyori.adventure.text.Component.text("OD_UI_CHECK:"+c.harness().runId()+":"+marker));
    }
    private void quit() { players.request("alpha", "quit"); players.await("actual quit", 100, () -> players.quits("alpha") == 1, () -> { c.observe("playerActions", players.journal()); c.finish(); }); }
    private void command(String step, Runnable next) { int before = commands; players.request("alpha", step); players.await("command " + step, 80, () -> commands > before, () -> c.later(2, next::run)); }
    @EventHandler(priority = EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent event) { commands++; }
}
