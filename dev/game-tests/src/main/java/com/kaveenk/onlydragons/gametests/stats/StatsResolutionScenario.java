package com.kaveenk.onlydragons.gametests.stats;

import static com.kaveenk.onlydragons.domain.stats.ModifierOperation.*;
import static com.kaveenk.onlydragons.domain.stats.StatKey.*;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;

/** Invokes the loaded production resolver using synthetic domain inputs, with no player or combat claim. */
public final class StatsResolutionScenario implements Scenario {
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("stats-calibration-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("resolver_loaded_from_production", true,
                StatResolver.class.getClassLoader() == context.production().getClass().getClassLoader());
        var profile = StatProfile.calibration();
        var resolver = new StatResolver(profile);
        var factory = new StatSnapshotFactory(profile);
        var weapon = new WeaponDefinition("stats-bow", 1, "fixture-v1", WeaponDefinition.FiringMode.DRAWN_BOW,
                100.25, List.of(new StatModifier("weapon", WEAPON_DAMAGE, FLAT, 0.75, 0)), List.of());
        var input = new ArrayList<>(List.of(
                new StatModifier("gear", WEAPON_DAMAGE, ADDITIVE_PERCENT, 50, 0),
                new StatModifier("gear", WEAPON_DAMAGE, MULTIPLIER, 2, 20),
                new StatModifier("gear", WEAPON_DAMAGE, MULTIPLIER, 0.5, 10),
                new StatModifier("gear", CRIT_CHANCE, FLAT, 175, 0),
                new StatModifier("gear", FEROCITY, FLAT, 750.5, 0)));
        var sources = ModifierSources.empty().replace("gear", input);
        var old = factory.create("fixture-1", weapon, sources);
        var snapshot = old.snapshot();
        context.check("complete_snapshot", 7, snapshot.values().size());
        context.check("weapon_base_once", 151.5, snapshot.raw(WEAPON_DAMAGE));
        context.check("raw_crit_above_100", 175.0, snapshot.raw(CRIT_CHANCE));
        context.check("ordinary_probability", 1.0, snapshot.ordinaryCritProbability());
        context.check("crit_damage_baseline", 50.0, snapshot.raw(CRIT_DAMAGE));
        context.check("ferocity_cap", List.of(750.5, 500.0), List.of(snapshot.raw(FEROCITY), snapshot.effective(FEROCITY)));
        context.check("explanation_values", List.of(101.0, 151.5, 75.75, 151.5, 151.5),
                old.explanations().get(WEAPON_DAMAGE).steps().stream().map(ExplainedStatSnapshot.Step::result).toList());
        Collections.reverse(input);
        context.check("deterministic_order", true, old.equals(factory.create("fixture-1", weapon,
                ModifierSources.empty().replace("gear", input))));
        input.clear();
        var refreshed = sources.replace("gear", List.of(new StatModifier("gear", WEAPON_DAMAGE, FLAT, 10, 0)));
        var next = factory.create("fixture-2", weapon, refreshed);
        context.check("source_replacement", List.of(111.0, 0.0, 0.0),
                List.of(next.snapshot().raw(WEAPON_DAMAGE), next.snapshot().raw(FEROCITY), next.snapshot().raw(CRIT_CHANCE)));
        boolean readOnly = false;
        try { old.explanations().get(WEAPON_DAMAGE).steps().clear(); }
        catch (UnsupportedOperationException expected) { readOnly = true; }
        context.check("old_snapshot_immutable", true, readOnly && old.equals(factory.create("fixture-1", weapon, sources))
                && old.snapshot().raw(WEAPON_DAMAGE) == 151.5);
        boolean rejected = false;
        try { resolver.resolve("invalid", Map.of(), ModifierSources.empty().replace("bad",
                List.of(new StatModifier("bad", FEROCITY, FLAT, -1, 0)))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        context.check("invalid_result_rejected", true, rejected);
        context.check("profile_revision", "stats-calibration-v1", old.profileRevision());
        context.observe("actor_scope", "Synthetic domain inputs on Paper; no authenticated player, entities, damage application or input simulation");
        context.observe("weapon_base", old.explanations().get(WEAPON_DAMAGE).base());
        context.observe("modifier_sources", snapshot.provenance().stream().map(StatModifier::sourceId).toList());
        context.finish();
    }
}
