package com.kaveenk.onlydragons.gametests;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Calibrates the real runtime and harness; it does not claim the planned combat exists. */
public final class CalibrationScenario implements Scenario {
    private final boolean deliberateFailure;
    public CalibrationScenario(boolean deliberateFailure) { this.deliberateFailure = deliberateFailure; }

    @Override public void start(ScenarioContext context) {
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("production_command_registered", true, context.production().getCommand("onlydragons") != null);
        String template = context.production().getConfig().getString("welcome-message", "Welcome, {player}!");
        context.check("production_service_initialized", template.replace("{player}", "Calibration"), context.production().greetings().welcome("Calibration"));

        ItemStack bow = new ItemStack(Material.BOW);
        NamespacedKey key = new NamespacedKey(context.harness(), "calibration-instance");
        String instance = UUID.randomUUID().toString();
        var meta = bow.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, instance);
        bow.setItemMeta(meta);
        ItemStack restored = ItemStack.deserializeBytes(bow.serializeAsBytes());
        context.check("paper_item_pdc_roundtrip", instance, restored.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));

        var world = Bukkit.getWorlds().getFirst();
        var start = world.getSpawnLocation().clone().add(0, 20, 0);
        context.tickChunk(start.getChunk());
        // Chunk generation/loading is asynchronous; a fixed short delay is not readiness evidence.
        context.later(1, () -> awaitTicking(context, start, 200));
    }

    private void awaitTicking(ScenarioContext context, org.bukkit.Location start, int remaining) {
        if (start.getChunk().getLoadLevel() == org.bukkit.Chunk.LoadLevel.ENTITY_TICKING) {
            context.observe("chunkReadinessWaitTicks", 200 - remaining);
            launch(context, start);
        } else if (remaining == 0) {
            throw new IllegalStateException("Test chunk did not reach ENTITY_TICKING: " + start.getChunk().getLoadLevel());
        } else {
            context.later(1, () -> awaitTicking(context, start, remaining - 1));
        }
    }

    private void launch(ScenarioContext context, org.bukkit.Location start) {
        var world = start.getWorld();
        context.check("test_chunk_entity_ticking", "ENTITY_TICKING", start.getChunk().getLoadLevel().name());
        Arrow arrow = context.own(world.spawn(start, Arrow.class));
        arrow.setGravity(false);
        arrow.setPersistent(false);
        arrow.setVelocity(new Vector(0.5, 0, 0));
        UUID uuid = arrow.getUniqueId();
        int launchTick = Bukkit.getCurrentTick();
        context.observe("arrowUuid", uuid.toString());
        context.observe("launchTick", launchTick);
        context.later(8, () -> {
            context.check("native_arrow_still_valid", true, arrow.isValid());
            context.check("native_arrow_uuid_continuous", uuid.toString(), arrow.getUniqueId().toString());
            context.check("server_ticks_advanced", true, Bukkit.getCurrentTick() - launchTick >= 8);
            double displacement = arrow.getLocation().distance(start);
            context.check("native_arrow_moved", true, displacement > 0.5);
            context.observe("observedTick", Bukkit.getCurrentTick());
            context.observe("arrowDisplacementBlocks", displacement);
            if (deliberateFailure) context.check("deliberate_failure", 1, 0);
            context.finish();
        });
    }
}
