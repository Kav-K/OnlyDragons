package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.application.proc.ProcCoordinator;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.enchant.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.paper.item.codec.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import org.bukkit.Bukkit;

/** Synthetic settled impacts; actual owned Paper callbacks drive the production queue and expiry. */
public final class EnchantProcScenario implements Scenario {
    private final UUID encounterId = UUID.randomUUID(), owner = UUID.randomUUID(), target = UUID.randomUUID();
    private final ProcCoordinator.Session session = new ProcCoordinator.Session(owner, UUID.randomUUID());
    private final CombatProfile profile = CombatProfile.calibration();
    private CombatEncounter encounter;
    private ProcCoordinator coordinator;
    private long startedTick;
    private ProcCoordinator.PhysicalResult parent;
    private final List<DamageResult> children = new ArrayList<>();

    @Override public void start(ScenarioContext c) {
        c.mechanicRevision("enchants-procs-v1");
        c.check("server_thread", true, Bukkit.isPrimaryThread());
        c.check("production_enabled", true, c.production().isEnabled());
        c.check("coordinator_loaded_from_production", true, c.production().getClass().getClassLoader() == ProcCoordinator.class.getClassLoader());
        c.observe("scope", "Synthetic settled physical impacts and session tokens; actual Paper scheduled ticks. No native collision, authenticated player, equipment listener or visual claim.");
        c.observe("effects_revision", EnchantEffects.REVISION);
        c.observe("limits", Map.of("queue", 8, "duePerTick", 2, "sessions", 1, "spacingTicks", 2));
        c.check("controlled_counts", List.of(0, 1, 0, 1, 3, 2, 5), List.of(
                Ferocity.count(0, () -> 0), Ferocity.count(25, () -> .249999), Ferocity.count(25, () -> .25),
                Ferocity.count(100, () -> .99), Ferocity.count(250, () -> .499999), Ferocity.count(250, () -> .5),
                Ferocity.count(500, () -> .99)));
        c.check("zero_base_tempo", 0.0, Ferocity.effective(0, new TempoState(0, 0).hit(5, 0).bonusAt(0)));
        c.check("snipe_displacement", List.of(.04, 0.0), List.of(
                EnchantEffects.snipeFraction(4, new Vector3(0, 0, 0), new Vector3(6, 8, 0)),
                EnchantEffects.snipeFraction(4, new Vector3(0, 0, 0), new Vector3(0, 0, 0))));
        itemPipeline(c);
        encounter = new CombatEncounter(new TargetState(encounterId, target, 100_000, 100_000, 100), "proc-test", profile);
        coordinator = new ProcCoordinator(encounter, new ProcCoordinator.Limits(8, 2, 1, 2), () -> 0);
        coordinator.activate(session);
        startedTick = Bukkit.getCurrentTick();
        var shot = shot(500, 5, false);
        parent = hit(shot, startedTick);
        c.check("parent_preincrement", 500.0, parent.damage().effectiveFerocity());
        c.check("five_stable_ids", true, parent.children().size() == 5
                && parent.children().stream().map(ProcCommand::procId).distinct().count() == 5
                && parent.children().getFirst().procId().equals(ProcCoordinator.childId(parent.damage().impactId(), 1)));
        c.check("captured_basis_and_crit", true, parent.children().stream().allMatch(p -> p.preCapDamage() == 75
                && p.crit() == CritOutcome.CRITICAL && p.parentImpactId().equals(parent.damage().impactId())));
        c.check("duplicate_cannot_enqueue", "PHYSICAL_REJECTED", hit(shot, startedTick).admission().name());
        var rejected = hit(shot(500, 0, false), startedTick);
        c.check("capacity_rejection", List.of("CAPACITY_REJECTED", 5, 5L), List.of(rejected.admission().name(),
                coordinator.metrics().queued(), coordinator.metrics().capacityRejectedChildren()));
        c.later(1, () -> step(c, 1));
    }

    private void step(ScenarioContext c, int elapsed) {
        try {
            long tick = Bukkit.getCurrentTick();
            if (elapsed == 1) c.check("actual_tick_progress", true, tick > startedTick);
            var due = coordinator.tick(tick);
            children.addAll(due);
            if (elapsed == 1) c.check("nothing_early", 0, due.size());
            if (elapsed == 2) {
                c.check("first_due_tick", List.of(1, startedTick + 2), List.of(due.size(), due.getFirst().tick()));
                c.check("eligible_child_builds_tempo", 100, coordinator.tempoBonus(session, tick));
            }
            if (elapsed == 10) {
                c.check("five_children_no_recursion", List.of(5, 0, 7), List.of(children.size(), coordinator.metrics().queued(), encounter.impacts().size()));
                c.check("child_damage_inheritance", true, children.stream().allMatch(r -> r.accepted()
                        && r.amounts().mitigatedDamage() == 75 && r.amounts().contributionDamage() == 75
                        && r.crit() == parent.damage().crit()));
                c.check("tempo_cap", 200, coordinator.tempoBonus(session, tick));
                c.check("accounting_totals", List.of(525.0, 99_475.0), List.of(encounter.contributions().get(owner).contributionDamage(), encounter.target().currentHealth()));
                var swapped = hit(shot(25, 0, true), tick);
                c.check("two_bow_swap", List.of(75.0, 15.0, "DUPLEX"), List.of(swapped.damage().effectiveFerocity(),
                        swapped.damage().amounts().mitigatedDamage(), swapped.damage().kind().name()));
            }
            if (elapsed == 12) c.check("duplex_child_scaled", 15.0, due.getFirst().amounts().mitigatedDamage());
            if (elapsed == 69) c.check("tempo_before_expiry", 200, coordinator.tempoBonus(session, tick));
            if (elapsed == 70) {
                c.check("tempo_at_expiry", 0, coordinator.tempoBonus(session, tick));
                var after = hit(shot(25, 0, true), tick);
                c.check("duplex_did_not_refresh", 25.0, after.damage().effectiveFerocity());
                coordinator.clearSession(session);
                c.check("quit_clears_queue_and_buff", List.of(0, 0, 0), List.of(coordinator.metrics().queued(),
                        coordinator.metrics().sessions(), coordinator.metrics().tempoStates()));
                var newSession = new ProcCoordinator.Session(owner, UUID.randomUUID());
                coordinator.activate(newSession);
                coordinator.clearSession(session); // late old-generation callback
                c.check("late_quit_preserves_reconnect", 1, coordinator.metrics().sessions());
                var old = hit(shot(100, 5, false), tick);
                c.check("old_session_cannot_buff", List.of("INACTIVE_SESSION", 0), List.of(old.admission().name(), coordinator.tempoBonus(newSession, tick)));
                hit(shot(100, 5, false), tick, newSession);
                encounter.end(); // production lifecycle boundary, no late target credit
            }
            if (elapsed == 72) {
                c.check("ended_target_rejects_child", "ENCOUNTER_ENDED", due.getFirst().rejectionReason().orElseThrow().name());
                c.check("rejected_child_no_credit", 0.0, due.getFirst().amounts().contributionDamage());
                coordinator.close();
                c.check("shutdown_clears_state", List.of(0, 0, 0), List.of(coordinator.metrics().queued(),
                        coordinator.metrics().sessions(), coordinator.metrics().tempoStates()));
                boolean rejected = false;
                try { coordinator.tick(tick + 1); } catch (IllegalStateException expected) { rejected = true; }
                c.check("late_callback_rejected", true, rejected);
                c.observe("ticks_observed", tick - startedTick);
                c.finish();
                return;
            }
            c.later(1, () -> step(c, elapsed + 1));
        } catch (RuntimeException | AssertionError failure) {
            coordinator.close();
            throw failure;
        }
    }

    private void itemPipeline(ScenarioContext c) {
        var registry = CalibrationLoadouts.registry();
        var codec = new WeaponItemCodec(registry);
        var item = registry.edit(registry.create("ordinary"), Map.of("power", 5, "vicious", 5, "snipe", 4), List.of());
        var stack = ItemStack.deserializeBytes(codec.encode(item).serializeAsBytes());
        var resolved = ((ItemReadResult.Valid) codec.decode(stack)).item();
        var stats = new StatSnapshotFactory(StatProfile.calibration()).create("proc-item-fixture-v1",
                resolved.resolvedWeapon(), ModifierSources.empty()).snapshot();
        var captured = new ShotContext(encounterId, UUID.randomUUID(), UUID.randomUUID(), 0, Optional.empty(), owner,
                resolved.instance().identity(), stats, resolved.enchantments(), profile.mechanic(), 0,
                new Vector3(0, 0, 0), new Vector3(1, 0, 0), CritOutcome.NORMAL, 1, 1);
        var edited = registry.edit(item, Map.of("vicious", 1), List.of());
        codec.encode(edited); // same item UUID can now represent different held contents
        c.check("trusted_snapshot_vicious_once", List.of(100.0, 50.0, 5.0), List.of(stats.effective(StatKey.WEAPON_DAMAGE),
                stats.effective(StatKey.CRIT_DAMAGE), stats.effective(StatKey.FEROCITY)));
        var engine = new CombatEncounter(new TargetState(encounterId, target, 1000, 1000, 100), "item-proc-test", profile);
        try (var procs = new ProcCoordinator(engine, new ProcCoordinator.Limits(5, 5, 1, 2), () -> 0)) {
            procs.activate(session);
            var position = new Vector3(10, 0, 0);
            var modifiers = EnchantEffects.calibration().modifiers(captured, position, false);
            var hit = procs.physical(captured, new PhysicalImpact(new PhysicalImpact.Key(encounterId, captured.projectileId(), target, 0),
                    owner, 0, position, Optional.empty()), modifiers, session, Optional.empty());
            c.check("codec_to_effects_to_combat", List.of(.4, .04, 72.0, 1), List.of(modifiers.additiveFractions().get("enchant:power"),
                    modifiers.additiveFractions().get("enchant:snipe"), hit.damage().amounts().mitigatedDamage(), hit.requestedChildren()));
            c.check("item_edit_cannot_rewrite_child", List.of(72.0, 5.0), List.of(procs.tick(2).getFirst().amounts().contributionDamage(),
                    captured.stats().effective(StatKey.FEROCITY)));
        }
    }

    private ShotContext shot(double ferocity, int tempo, boolean duplex) {
        var stats = new StatResolver(StatProfile.calibration()).resolve("proc-fixture-v1", Map.of(StatKey.WEAPON_DAMAGE, 100.0,
                StatKey.FEROCITY, ferocity), ModifierSources.empty()).snapshot();
        List<WeaponDefinition.Enchantment> enchants = tempo > 0
                ? List.of(new WeaponDefinition.Enchantment("fatal_tempo", tempo, WeaponDefinition.EnchantmentKind.ULTIMATE))
                : duplex ? List.of(new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE)) : List.of();
        return new ShotContext(encounterId, UUID.randomUUID(), UUID.randomUUID(), 0, duplex ? Optional.of(UUID.randomUUID()) : Optional.empty(),
                owner, new WeaponIdentity(UUID.randomUUID(), "proc-test", 1, "v1"), stats, enchants, profile.mechanic(), 0,
                new Vector3(0, 0, 0), new Vector3(1, 0, 0), CritOutcome.CRITICAL, 1, duplex ? .2 : 1);
    }
    private ProcCoordinator.PhysicalResult hit(ShotContext shot, long tick) {
        return hit(shot, tick, session);
    }
    private ProcCoordinator.PhysicalResult hit(ShotContext shot, long tick, ProcCoordinator.Session captured) {
        var impact = new PhysicalImpact(new PhysicalImpact.Key(encounterId, shot.projectileId(), target, 0), owner, tick, new Vector3(10, 0, 0), Optional.empty());
        return coordinator.physical(shot, impact, DamageModifiers.none(), captured, Optional.empty());
    }
}
