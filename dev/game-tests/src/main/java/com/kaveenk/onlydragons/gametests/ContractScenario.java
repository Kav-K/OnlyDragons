package com.kaveenk.onlydragons.gametests;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CritOutcome;
import com.kaveenk.onlydragons.domain.combat.DamageResult;
import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import com.kaveenk.onlydragons.domain.encounter.TargetState;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import com.kaveenk.onlydragons.domain.stats.StatValue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.util.Vector;

/**
 * Loaded-production contract calibration with a real moving arrow and synthetic domain DTOs.
 * It mutates source collections after capture to prove immutable shot provenance.
 * The 210/52.5/20 damage values are independently constructed contract examples,
 * not collision-driven combat or a claim about current production proc policy.
 */
public final class ContractScenario implements Scenario {
    private static final String REVISION = "contract-fixture-v1";
    private static final MechanicRevision PROFILE = new MechanicRevision("contract-calibration", REVISION);

    /**
     * Checks the production classloader boundary and acquires a ticking native-flight chunk.
     * @param context current scenario's server-thread owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision(REVISION);
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("contracts_loaded_from_production", true,
                StatSnapshot.class.getClassLoader() == context.production().getClass().getClassLoader());
        context.observe("evidenceScope", "Production contract consumers on Paper; no production resolver, damage application, collision, or player input is implemented by this scenario.");
        context.observe("ferocityHealthFraction", 0.25);
        context.observe("coefficientScope", "Test fixture only; not a production mechanic default.");

        var world = Bukkit.getWorlds().getFirst();
        var spawn = world.getSpawnLocation();
        var chunk = spawn.getChunk();
        // Stay in the center of one owned chunk throughout this short native flight.
        var start = new Location(world, chunk.getX() * 16 + 8.0, spawn.getY() + 20, chunk.getZ() * 16 + 8.0);
        context.tickChunk(chunk);
        context.later(1, () -> awaitTicking(context, start, 200));
    }

    /**
     * Waits for actual entity ticking before creating the arrow used by the capture example.
     */
    private void awaitTicking(ScenarioContext context, Location start, int remaining) {
        if (start.getChunk().getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING) {
            context.observe("chunkReadinessWaitTicks", 200 - remaining);
            capture(context, start);
        } else if (remaining == 0) {
            throw new IllegalStateException("Contract test chunk did not reach ENTITY_TICKING");
        } else {
            context.later(1, () -> awaitTicking(context, start, remaining - 1));
        }
    }

    /**
     * Captures immutable shot inputs, then deliberately changes the mutable source collections.
     */
    private void capture(ScenarioContext context, Location start) {
        context.check("test_chunk_entity_ticking", "ENTITY_TICKING", start.getChunk().getLoadLevel().name());
        Arrow arrow = context.own(start.getWorld().spawn(start, Arrow.class));
        arrow.setGravity(false);
        arrow.setPersistent(false);
        arrow.setVelocity(new Vector(0.1, 0, 0));
        int launchTick = Bukkit.getCurrentTick();

        var values = new EnumMap<StatKey, StatValue>(StatKey.class);
        for (StatKey key : StatKey.values()) values.put(key, new StatValue(0, 0));
        values.put(StatKey.WEAPON_DAMAGE, new StatValue(100, 100));
        values.put(StatKey.CRIT_CHANCE, new StatValue(175, 175));
        values.put(StatKey.CRIT_DAMAGE, new StatValue(50, 50));
        var modifiers = new ArrayList<>(List.of(
                new StatModifier("gear:calibration-bow", StatKey.WEAPON_DAMAGE, ModifierOperation.FLAT, 100, 0),
                new StatModifier("preset:critical", StatKey.CRIT_CHANCE, ModifierOperation.FLAT, 175, 0),
                new StatModifier("preset:critical", StatKey.CRIT_DAMAGE, ModifierOperation.FLAT, 50, 0)));
        var enchantments = new ArrayList<>(List.of(
                new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE),
                new WeaponDefinition.Enchantment("power", 4, WeaponDefinition.EnchantmentKind.ORDINARY)));
        var snapshot = new StatSnapshot("equipment-contract-v1", values, modifiers);
        var shot = new ShotContext(UUID.randomUUID(), UUID.randomUUID(), arrow.getUniqueId(), 0,
                Optional.empty(), UUID.randomUUID(), new WeaponIdentity(UUID.randomUUID(), "calibration_bow", 1, "v1"),
                snapshot, enchantments, PROFILE, launchTick, new Vector3(start.getX(), start.getY(), start.getZ()),
                new Vector3(0.1, 0, 0), CritOutcome.CRITICAL, 1, 1);

        // Simulate later gear/source input changes; accepted shot data must remain frozen.
        values.put(StatKey.WEAPON_DAMAGE, new StatValue(900, 900));
        values.put(StatKey.CRIT_CHANCE, new StatValue(0, 0));
        modifiers.clear();
        enchantments.clear();
        enchantments.add(new WeaponDefinition.Enchantment("fatal_tempo", 5, WeaponDefinition.EnchantmentKind.ULTIMATE));
        context.observe("arrowUuid", shot.projectileId().toString());
        context.observe("launchTick", launchTick);
        context.later(8, () -> verify(context, start, arrow, launchTick, shot));
    }

    /**
     * Checks native UUID continuity and that captured stats/enchants survived later source mutation.
     */
    private void verify(ScenarioContext context, Location start, Arrow arrow, int launchTick, ShotContext shot) {
        context.check("native_arrow_valid", true, arrow.isValid());
        context.check("native_arrow_uuid_captured", shot.projectileId().toString(), arrow.getUniqueId().toString());
        context.check("server_ticks_advanced", true, Bukkit.getCurrentTick() - launchTick >= 8);
        double displacement = arrow.getLocation().distance(start);
        context.check("native_arrow_moved", true, displacement > 0.25);
        context.observe("arrowDisplacementBlocks", displacement);
        context.observe("observedTick", Bukkit.getCurrentTick());
        context.check("snapshot_revision_retained", "equipment-contract-v1", shot.stats().revision());
        context.check("weapon_damage_capture_immutable", 100.0, shot.stats().raw(StatKey.WEAPON_DAMAGE));
        context.check("raw_crit_retained_above_100", 175.0, shot.stats().raw(StatKey.CRIT_CHANCE));
        context.check("ordinary_crit_probability_capped", 1.0, shot.stats().ordinaryCritProbability());
        context.check("source_provenance_retained", List.of("gear:calibration-bow", "preset:critical", "preset:critical"),
                shot.stats().provenance().stream().map(StatModifier::sourceId).toList());
        context.check("captured_enchants_survive_swap", List.of("duplex", "power"),
                shot.enchantments().stream().map(WeaponDefinition.Enchantment::id).toList());
        context.check("snapshot_map_read_only", true, rejectsMutation(() -> shot.stats().values().clear()));
        context.check("snapshot_provenance_read_only", true, rejectsMutation(() -> shot.stats().provenance().clear()));
        context.check("shot_enchants_read_only", true, rejectsMutation(() -> shot.enchantments().clear()));
        context.check("one_ultimate_enforced", true, rejectsInvalid(() -> WeaponDefinition.validatedEnchantments(List.of(
                new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE),
                new WeaponDefinition.Enchantment("fatal_tempo", 5, WeaponDefinition.EnchantmentKind.ULTIMATE)))));
        context.check("nonfinite_stat_rejected", true, rejectsInvalid(() -> new StatValue(Double.NaN, 0)));
        verifyDamageFixtures(context, shot);
        context.finish();
    }

    /**
     * Constructs explicit DTO arithmetic and rejection examples without applying native damage.
     */
    private void verifyDamageFixtures(ScenarioContext context, ShotContext shot) {
        // Explicit fixture math, not an implementation of the future production damage engine.
        double ordinary = shot.stats().effective(StatKey.WEAPON_DAMAGE) * 1.4
                * (1 + shot.stats().effective(StatKey.CRIT_DAMAGE) / 100.0);
        context.check("ordinary_critical_fixture_210", true, Math.abs(ordinary - 210) < 1e-9);
        context.observe("ordinaryCriticalDamage", ordinary);
        UUID targetId = UUID.randomUUID();
        var origin = new PhysicalImpact.Key(shot.encounterId(), shot.projectileId(), targetId, 0);
        UUID parent = UUID.randomUUID();
        var amounts = new DamageResult.Amounts(ordinary, ordinary, ordinary, ordinary * 0.25, 20, ordinary);
        var result = new DamageResult(UUID.randomUUID(), Optional.of(parent), origin, shot.ownerId(), shot.shotId(),
                DamageResult.Kind.FEROCITY, Bukkit.getCurrentTick(), shot.mechanic(), amounts,
                shot.crit(), 25, Map.of("fixture_additive_percent", 40.0), Optional.empty());
        context.check("ferocity_requested_hp_52_5", 52.5, result.amounts().requestedHealthDamage());
        context.check("ferocity_actual_hp_20", 20.0, result.amounts().actualHealthDamage());
        context.check("ferocity_contribution_210", 210.0, result.amounts().contributionDamage());
        context.check("ferocity_parent_retained", parent.toString(), result.parentImpactId().orElseThrow().toString());
        context.check("rejected_hit_cannot_earn_score", true, rejectsInvalid(() -> new DamageResult(
                UUID.randomUUID(), Optional.of(parent), origin, shot.ownerId(), shot.shotId(), DamageResult.Kind.FEROCITY,
                Bukkit.getCurrentTick(), PROFILE, amounts, shot.crit(), 25, Map.of(),
                Optional.of(DamageResult.RejectionReason.TARGET_DEAD))));
        var completedTarget = new TargetState(shot.encounterId(), targetId, 1000, 0, 0);
        context.check("zero_hp_target_is_dead", false, completedTarget.alive());
    }

    /**
     * Requires UnsupportedOperationException specifically; another failure is not an immutability pass.
     */
    private static boolean rejectsMutation(Runnable action) {
        try { action.run(); return false; } catch (UnsupportedOperationException expected) { return true; }
    }

    /**
     * Requires IllegalArgumentException specifically for the declared invalid domain input.
     */
    private static boolean rejectsInvalid(Runnable action) {
        try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; }
    }
}
