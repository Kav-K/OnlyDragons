package com.kaveenk.onlydragons.gametests.combat;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.*;
import org.bukkit.Bukkit;

/**
 * Production accounting calibration driven by synthetic domain impacts on real Paper.
 * Literal cap boundaries, overkill credit and reduced/zero proc HP fractions are
 * independent oracles. No packet firing, native collision or suppression is implied;
 * those belong to connected gameplay scenarios.
 */
public final class CombatAccountingScenario implements Scenario {
    private static final UUID ENCOUNTER = new UUID(0, 1), OWNER = new UUID(0, 2), TARGET = new UUID(0, 3);
    private static final DamageModifiers POWER = new DamageModifiers(Map.of("power", 0.4), Map.of());

    /**
     * Checks atomic acceptance/rejection, inherited proc amounts and frozen completion.
     * @param context current server-thread report owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("combat-accounting-calibration-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("services_loaded_from_production", true,
                CombatEncounter.class.getClassLoader() == context.production().getClass().getClassLoader()
                && DamageCalculator.class.getClassLoader() == context.production().getClass().getClassLoader());
        var full = CombatProfile.calibration();
        var reduced = new CombatProfile(new MechanicRevision("reduced-hp-test-calibration", "v1"),
                CombatProfile.Mitigation.NONNEGATIVE_DEFENSE, CombatProfile.Cap.NONE, 0.25);
        var stats = stats(100, 175);
        context.check("crit_baseline_and_raw_chance", List.of(50.0, 175.0, 1.0),
                List.of(stats.effective(StatKey.CRIT_DAMAGE), stats.raw(StatKey.CRIT_CHANCE), stats.ordinaryCritProbability()));
        var roller = new CritResolver();
        context.check("crit_threshold", List.of("NORMAL", "CRITICAL", "CRITICAL", "NORMAL"), List.of(
                roller.roll(stats(100, 0), () -> 0).name(), roller.roll(stats, () -> 0.99).name(),
                roller.roll(stats(100, 25), () -> Math.nextDown(0.25)).name(), roller.roll(stats(100, 25), () -> 0.25).name()));
        var encounter = encounter(230, 0, reduced);
        var shot = shot(4, 100, reduced, CritOutcome.CRITICAL, 1, Optional.empty());
        var first = hit(encounter, shot, POWER);
        context.check("golden_critical_210", List.of(210.0, 210.0, 210.0), List.of(
                first.amounts().rawOffense(), first.amounts().actualHealthDamage(), first.amounts().contributionDamage()));
        context.check("named_modifier", 0.4, first.modifierBreakdown().get("additive/power"));
        context.check("duplicate_physical", "DUPLICATE_IMPACT", hit(encounter, shot, POWER).rejectionReason().orElseThrow().name());
        var lethal = encounter.proc(child(first, 8), 22);
        context.check("separate_health_score_floor", List.of(52.5, 20.0, 210.0, 0.0), List.of(
                lethal.amounts().requestedHealthDamage(), lethal.amounts().actualHealthDamage(),
                lethal.amounts().contributionDamage(), encounter.target().currentHealth()));
        var result = encounter.completion().orElseThrow();
        context.check("late_descendant", "TARGET_DEAD", encounter.proc(child(first, 9), 22).rejectionReason().orElseThrow().name());
        var second = hit(encounter, shot(5, 100, reduced, CritOutcome.CRITICAL, 1, Optional.empty()), POWER);
        context.check("second_lethal_candidate", "TARGET_DEAD", second.rejectionReason().orElseThrow().name());
        context.check("single_frozen_completion", true, result == encounter.completion().orElseThrow()
                && result.completionId().equals(lethal.impactId()) && encounter.impacts().size() == 2);
        context.check("final_totals", List.of(230.0, 420.0), List.of(result.participants().get(OWNER).actualHealthDamage(),
                result.participants().get(OWNER).contributionDamage()));
        boolean readOnly = false;
        try { result.participants().clear(); } catch (UnsupportedOperationException expected) { readOnly = true; }
        context.check("result_immutable", true, readOnly);
        var simultaneous = encounter(100, 0, full);
        var winner = hit(simultaneous, shot(4, 100, full, CritOutcome.CRITICAL, 1, Optional.empty()), POWER);
        var loser = hit(simultaneous, shot(5, 100, full, CritOutcome.CRITICAL, 1, Optional.empty()), POWER);
        context.check("same_tick_physical_lethals", true, winner.tick() == loser.tick()
                && winner.amounts().actualHealthDamage() == 100 && winner.amounts().contributionDamage() == 210
                && loser.rejectionReason().orElseThrow() == DamageResult.RejectionReason.TARGET_DEAD
                && simultaneous.completion().orElseThrow().completionId().equals(winner.impactId())
                && simultaneous.impacts().size() == 1);
        var historical = CombatProfile.dragonExperiment(new MechanicRevision("historical-cap-test-calibration", "v1"), 0.25);
        context.check("historical_cap_boundaries", List.of(0.0, 4000.0, 6000.0, 8000.0, 10000.0, 10000.0),
                Arrays.stream(new double[]{0, 4000, 24000, 224000, 2224000, Double.MAX_VALUE})
                        .map(value -> historical.cap(value, 1_000_000)).boxed().toList());
        context.check("cap_adjacent_boundaries", true, capNeighbors(historical));
        context.check("uncapped_profile", 2_224_000.0, full.cap(2_224_000, 1_000_000));
        var capped = encounter(1_000_000, 100, historical);
        var parent = hit(capped, shot(4, 48000, historical, CritOutcome.NORMAL, 1, Optional.empty()), DamageModifiers.none());
        var proc = capped.proc(child(parent, 8), 22);
        context.check("inherited_parent_and_crit", true, proc.parentImpactId().orElseThrow().equals(parent.impactId())
                && proc.crit() == parent.crit() && proc.mechanic().equals(parent.mechanic()));
        context.check("mitigation_and_single_child_cap", List.of(24000.0, 6000.0, 6000.0, 1500.0, 992500.0), List.of(
                parent.amounts().mitigatedDamage(), parent.amounts().cappedDamage(), proc.amounts().cappedDamage(),
                proc.amounts().actualHealthDamage(), capped.target().currentHealth()));
        context.check("duplicate_proc", "DUPLICATE_IMPACT", capped.proc(child(parent, 8), 22).rejectionReason().orElseThrow().name());
        boolean recursiveRejected = false;
        try { capped.proc(child(proc, 9), 22); } catch (IllegalArgumentException expected) { recursiveRejected = true; }
        context.check("recursive_child_rejected", true, recursiveRejected);
        var scoreOnly = new CombatProfile(new MechanicRevision("score-only-test-calibration", "v1"),
                CombatProfile.Mitigation.NONNEGATIVE_DEFENSE, CombatProfile.Cap.NONE, 0);
        var duplex = encounter(1000, 0, scoreOnly);
        var scaled = hit(duplex, shot(4, 100, scoreOnly, CritOutcome.CRITICAL, 0.2, Optional.of(new UUID(0, 7))), POWER);
        var ghost = duplex.proc(child(scaled, 8), 22);
        context.check("duplex_score_only_inheritance", List.of(42.0, 0.0, 42.0, 958.0), List.of(
                scaled.amounts().rawOffense(), ghost.amounts().actualHealthDamage(), ghost.amounts().contributionDamage(), duplex.target().currentHealth()));
        capped.end();
        context.check("ended_generation", "ENCOUNTER_ENDED", capped.proc(child(parent, 10), 22).rejectionReason().orElseThrow().name());
        var cancelled = encounter(1000, 0, full);
        var cancelShot = shot(4, 100, full, CritOutcome.NORMAL, 1, Optional.empty());
        var rejected = cancelled.physical(cancelShot, impact(cancelShot), POWER, 25, Optional.of(DamageResult.RejectionReason.CANCELLED));
        context.check("settled_cancellation", true, !rejected.accepted() && cancelled.impacts().isEmpty()
                && cancelled.target().currentHealth() == 1000 && cancelled.contributions().isEmpty());
        boolean overflowRejected = false;
        try { hit(cancelled, cancelShot, new DamageModifiers(Map.of(), Map.of("overflow", Double.MAX_VALUE))); }
        catch (IllegalArgumentException expected) { overflowRejected = true; }
        context.check("overflow_atomic", true, overflowRejected && cancelled.target().currentHealth() == 1000 && cancelled.impacts().isEmpty());
        context.check("profile_retained", reduced.mechanic().toString(), result.mechanic().toString());
        context.observe("actor_scope", "Synthetic domain impacts on actual Paper. No physical collision, native suppression, authenticated client, input or visual validation.");
        context.observe("calibration", "0.25 and 0 ferocity HP fractions are experiments, not empirical Hypixel values; historical cap is the versioned 2021 mapping.");
        context.observe("completion_id", result.completionId().toString());
        context.finish();
    }

    /**
     * Checks both sides of each historical piecewise cap boundary against explicit slopes.
     */
    private static boolean capNeighbors(CombatProfile profile) {
        double[][] fixtures = {{4000, 4000, 1, 0.1}, {24000, 6000, 0.1, 0.01},
                {224000, 8000, 0.01, 0.001}, {2224000, 10000, 0.001, 0}};
        for (double[] f : fixtures) {
            if (Math.abs(profile.cap(f[0] - 1, 1_000_000) - (f[1] - f[2])) > 1e-8
                    || Math.abs(profile.cap(f[0] + 1, 1_000_000) - (f[1] + f[3])) > 1e-8) return false;
        }
        return true;
    }
    /**
     * Resolves a synthetic calibrated weapon/chance pair through production stat code.
     */
    private static StatSnapshot stats(double damage, double chance) {
        return new StatResolver(StatProfile.calibration()).resolve("fixture-v1",
                Map.of(StatKey.WEAPON_DAMAGE, damage, StatKey.CRIT_CHANCE, chance), ModifierSources.empty()).snapshot();
    }
    /**
     * Creates a fresh isolated domain ledger with the declared HP/defense/profile.
     */
    private static CombatEncounter encounter(double hp, double defense, CombatProfile profile) {
        return new CombatEncounter(new TargetState(ENCOUNTER, TARGET, hp, hp, defense), "practice", profile);
    }
    /**
     * Creates deterministic synthetic shot ancestry and captured launch values; no arrow is spawned.
     */
    private static ShotContext shot(long id, double damage, CombatProfile profile, CritOutcome crit, double scale, Optional<UUID> parent) {
        return new ShotContext(ENCOUNTER, new UUID(1, id), new UUID(0, id), 0, parent, OWNER,
                new WeaponIdentity(new UUID(2, id), "test-bow", 1, "v1"), stats(damage, 100), List.of(), profile.mechanic(),
                10, new Vector3(0, 100, 0), new Vector3(0, 1, 0), crit, 1, scale);
    }
    /**
     * Builds a synthetic accepted-candidate key at the fixture's declared collision tick.
     */
    private static PhysicalImpact impact(ShotContext shot) {
        return new PhysicalImpact(new PhysicalImpact.Key(shot.encounterId(), shot.projectileId(), TARGET, 0), OWNER, 20,
                new Vector3(0, 110, 0), Optional.empty());
    }
    /**
     * Invokes the domain physical receiver directly, distinct from native event acceptance.
     */
    private static DamageResult hit(CombatEncounter encounter, ShotContext shot, DamageModifiers modifiers) {
        return encounter.physical(shot, impact(shot), modifiers, 25, Optional.empty());
    }
    /**
     * Copies parent provenance and mitigated damage into a synthetic one-generation child.
     */
    private static ProcCommand child(DamageResult parent, long id) {
        return new ProcCommand(new UUID(0, id), parent.impactId(), parent.origin(), parent.ownerId(), parent.shotId(), 22,
                parent.amounts().mitigatedDamage(), parent.crit(), parent.mechanic(), 0);
    }
}
