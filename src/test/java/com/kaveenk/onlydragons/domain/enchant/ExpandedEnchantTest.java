package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.application.proc.ProcCoordinator;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalogLoader;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ExpandedEnchantTest {
    final ItemRegistry registry = CalibrationLoadouts.compatibleRegistry();
    final UUID encounter = UUID.randomUUID(), target = UUID.randomUUID(), owner = UUID.randomUUID();

    StatSnapshot stats(double cc) {
        return new StatResolver(StatProfile.calibration()).resolve("test", Map.of(StatKey.CRIT_CHANCE, cc), ModifierSources.empty()).snapshot();
    }

    @ParameterizedTest @CsvSource({"0,0,false", "99,0,false", "100,0,false", "125,.249999,true", "125,.25,false", "199,.989999,true", "199,.99,false", "200,.999999,true", "1000000,.999999,true"})
    void rawChanceAndStrictMegaThreshold(double cc, double sample, boolean expected) {
        var count = new AtomicInteger();
        var capture = OverloadCapture.roll(5, stats(cc), () -> { count.incrementAndGet(); return sample; });
        assertEquals(expected, capture.megaCritical());
        assertEquals(cc, capture.rawCritChance());
        assertEquals(1, count.get());
        assertEquals(Math.min(1, cc / 100), stats(cc).ordinaryCritProbability());
    }

    @ParameterizedTest @CsvSource({"1,1.1", "2,1.2", "3,1.3", "4,1.4", "5,1.5"})
    void levelStatsOnceAndMultiplier(int level, double factor) {
        var item = registry.resolve(registry.edit(registry.create("ordinary_v3"), Map.of("overload", level), List.of()));
        var snapshot = new StatSnapshotFactory(StatProfile.calibration()).create("test", item.resolvedWeapon(), ModifierSources.empty()).snapshot();
        assertEquals(level, snapshot.raw(StatKey.CRIT_CHANCE));
        assertEquals(50 + level, snapshot.raw(StatKey.CRIT_DAMAGE));
        assertEquals(factor, OverloadCapture.roll(level, stats(200), () -> .9).multiplier());
        assertEquals(1, OverloadCapture.roll(level, stats(100), () -> 0).multiplier());
    }

    @Test void ordinaryDrawContractAndAbsentOverload() {
        for (double cc : new double[]{0, 100, 200}) {
            var draws = new AtomicInteger();
            var random = (com.kaveenk.onlydragons.application.RandomSource) () -> { draws.incrementAndGet(); return .5; };
            new CritResolver().roll(stats(cc), random);
            assertEquals(1, draws.get());
            assertEquals(OverloadCapture.absent(), OverloadCapture.roll(0, stats(cc), random));
            assertEquals(1, draws.get());
            OverloadCapture.roll(1, stats(cc), random);
            assertEquals(2, draws.get());
        }
        for (double invalid : new double[]{-1, 1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> OverloadCapture.roll(1, stats(150), () -> invalid));
        for (int level : new int[]{-1, 6}) assertThrows(IllegalArgumentException.class, () -> OverloadCapture.roll(level, stats(200), () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new OverloadCapture("unknown", 0, 0, OptionalDouble.empty(), false));
        assertThrows(IllegalArgumentException.class, () -> new OverloadCapture(OverloadCapture.REVISION, 5, 125, OptionalDouble.of(.25), true));
    }

    @ParameterizedTest @CsvSource({"1,.05", "2,.10", "3,.15", "4,.20", "5,.30", "6,.40"})
    void gravityDescriptorTableAndControl(int level, double fraction) {
        var shot = shot(Map.of("gravity", level), CombatProfile.calibration(), 0, 0);
        var effects = EnchantEffects.checkpointTwo();
        assertEquals(Map.of("enchant:gravity/" + OverloadCapture.REVISION, fraction), effects.modifiers(shot, shot.launchPosition(), true).additiveFractions());
        assertEquals(DamageModifiers.none(), effects.modifiers(shot, shot.launchPosition(), false));
        assertThrows(ItemValidationException.class, () -> registry.edit(registry.create("ordinary_v3"), Map.of("gravity", 7), List.of()));
        assertThrows(ItemValidationException.class, () -> registry.edit(registry.create("ordinary_v3"), Map.of("dragon_hunter", 1), List.of()));
    }

    @Test void exactCatalogRoutingAvailabilityAndRetainedSelection() {
        var expected = Map.of("dragon_tracer",5,"duplex",5,"fatal_tempo",5,"power",7,"vicious",5,"snipe",4,"overload",5,"gravity",6,"infinite_quiver",10,"flame",2);
        assertEquals(expected.keySet(), registry.enchantments().keySet());
        expected.forEach((id, max) -> {
            var descriptor = registry.catalog(CalibrationLoadouts.EXPANDED_REVISION).enchant(id);
            assertEquals(max.intValue(), descriptor.maxLevel());
            assertEquals(Set.of(WeaponDefinition.FiringMode.DRAWN_BOW, WeaponDefinition.FiringMode.SHORTBOW), descriptor.compatibleModes());
            assertEquals(!Set.of("infinite_quiver","flame").contains(id), descriptor.available());
            assertThrows(ItemValidationException.class, () -> descriptor.validate(0, WeaponDefinition.FiringMode.DRAWN_BOW));
            assertThrows(ItemValidationException.class, () -> descriptor.validate(max + 1, WeaponDefinition.FiringMode.SHORTBOW));
        });
        for (var old : CalibrationLoadouts.registry().definitions().keySet()) {
            var instance = CalibrationLoadouts.registry().create(old);
            assertEquals(CalibrationLoadouts.registry().resolve(instance), registry.resolve(instance));
            assertEquals(CalibrationLoadouts.REVISION, registry.create(old).registryRevision());
            assertThrows(ItemValidationException.class, () -> registry.edit(instance, Map.of("overload", 1), List.of()));
        }
        var item = registry.create("ordinary_v3");
        var edited = registry.edit(item, Map.of("overload",5,"gravity",6), List.of());
        assertEquals(item.identity(), edited.identity());
        assertTrue(item.enchantLevels().isEmpty());
        for (String id : List.of("infinite_quiver","flame")) {
            assertEquals(ItemValidationException.Code.UNAVAILABLE_ENCHANT, assertThrows(ItemValidationException.class,
                    () -> registry.edit(item, Map.of(id, 1), List.of())).code());
            assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(item.identity(), item.registryRevision(), Map.of(id,1), List.of())));
        }
        assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(item.identity(), CalibrationLoadouts.REVISION, Map.of(), List.of())));
        assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(item.identity(), "calibration-items-v999", Map.of(), List.of())));
        assertThrows(ItemValidationException.class, () -> registry.edit(item, Map.of("duplex",5,"fatal_tempo",5), List.of()));
        assertEquals(DragonCatalogLoader.calibration(CalibrationLoadouts.registry()).bundled().select("test_dragon"), DragonCatalogLoader.calibration(registry).bundled().select("test_dragon"));
    }

    @Test void duplexAndFerocityKeepMegaBasisOnceWithCapAndHpPolicy() {
        var profile = CombatProfile.dragonExperiment(new MechanicRevision("expanded-cap-test", "v1"), .5);
        var shot = shot(Map.of("overload",5,"gravity",6,"power",7,"duplex",5), profile, 195, 100);
        var child = FiringRules.child(shot, UUID.randomUUID(), 1, 5);
        assertSame(shot.overload(), child.overload());
        assertSame(shot.stats(), child.stats());
        var combat = new CombatEncounter(new TargetState(encounter,target,1000,1000,100), "test", profile);
        var queue = new ProcCoordinator(combat, new ProcCoordinator.Limits(10,10,1,2), () -> { throw new AssertionError("No random draws at impact for whole Ferocity"); });
        var session = new ProcCoordinator.Session(owner,UUID.randomUUID());queue.activate(session);
        var first = hit(queue,shot,session,10);var second = hit(queue,child,session,11);
        // 100 * (1 + .65 + .40) * 1.55 * 1.5 = 476.625; defense halves before historical cap.
        assertEquals(476.625,first.damage().amounts().rawOffense(),1e-10);
        assertEquals(238.3125,first.damage().amounts().mitigatedDamage(),1e-10);
        assertEquals(8.0143125,first.damage().amounts().cappedDamage(),1e-10);
        assertEquals(47.6625,second.damage().amounts().mitigatedDamage(),1e-10);
        assertEquals(6.236625,second.damage().amounts().cappedDamage(),1e-10);
        var procs=queue.tick(13);assertEquals(2,procs.size());
        for(var proc:procs) {
            var parent=proc.parentImpactId().orElseThrow().equals(first.damage().impactId())?first.damage():second.damage();
            assertEquals(parent.modifierBreakdown(),proc.modifierBreakdown());
            assertEquals(parent.amounts().cappedDamage(),proc.amounts().contributionDamage());
            assertEquals(parent.amounts().cappedDamage()*.5,proc.amounts().actualHealthDamage());
        }
        assertEquals(0,queue.metrics().queued());
        assertEquals(2*(8.0143125+6.236625),combat.contributions().get(owner).contributionDamage(),1e-10);
        queue.close();assertEquals(0,queue.metrics().sessions());
    }

    @Test void terminalOverkillVetoAndOldShotsStayImmutable() {
        var profile=CombatProfile.calibration();var shot=shot(Map.of("overload",5),profile,195,100);
        var combat=new CombatEncounter(new TargetState(encounter,target,200,200,0),"test",profile);
        var queue=new ProcCoordinator(combat,new ProcCoordinator.Limits(10,10,1,2),()->0);
        var session=new ProcCoordinator.Session(owner,UUID.randomUUID());queue.activate(session);
        var impact=impact(shot,10);
        var veto=queue.physical(shot,impact,DamageModifiers.none(),session,Optional.of(DamageResult.RejectionReason.CANCELLED));
        assertFalse(veto.damage().accepted());assertEquals(200,combat.target().currentHealth());
        var result=hit(queue,shot,session,11).damage();
        assertEquals(232.5,result.amounts().contributionDamage());assertEquals(200,result.amounts().actualHealthDamage());
        var frozen=combat.completion().orElseThrow();queue.tick(20);
        assertEquals(frozen,combat.completion().orElseThrow());
        assertFalse(hit(queue,shot,session,21).damage().accepted());
        assertEquals(200,shot.stats().raw(StatKey.CRIT_CHANCE));
        assertTrue(shot.overload().megaCritical());
    }

    ShotContext shot(Map<String,Integer> enchants,CombatProfile profile,double cc,double ferocity) {
        var resolved=registry.resolve(registry.edit(registry.create("ordinary_v3"),enchants,List.of()));
        var extra=ModifierSources.empty().replace("test",List.of(new StatModifier("test",StatKey.CRIT_CHANCE,ModifierOperation.FLAT,cc,0),new StatModifier("test",StatKey.FEROCITY,ModifierOperation.FLAT,ferocity,0)));
        var stats=new StatSnapshotFactory(StatProfile.calibration()).create("captured",resolved.resolvedWeapon(),extra).snapshot();
        var crit=new CritResolver().roll(stats,()->.99);
        var capture=OverloadCapture.roll(enchants.getOrDefault("overload",0),stats,()->.99);
        return new ShotContext(encounter,UUID.randomUUID(),UUID.randomUUID(),0,Optional.empty(),owner,resolved.instance().identity(),stats,resolved.enchantments(),profile.mechanic(),0,new Vector3(0,0,0),new Vector3(1,0,0),crit,1,1,capture);
    }
    PhysicalImpact impact(ShotContext shot,long tick) {return new PhysicalImpact(new PhysicalImpact.Key(encounter,shot.projectileId(),target,0),owner,tick,shot.launchPosition(),Optional.empty());}
    ProcCoordinator.PhysicalResult hit(ProcCoordinator q,ShotContext shot,ProcCoordinator.Session session,long tick) {
        return q.physical(shot,impact(shot,tick),EnchantEffects.checkpointTwo().modifiers(shot,shot.launchPosition(),true),session,Optional.empty());
    }
}
