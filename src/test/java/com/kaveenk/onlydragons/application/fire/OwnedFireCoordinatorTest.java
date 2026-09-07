package com.kaveenk.onlydragons.application.fire;

import com.kaveenk.onlydragons.application.proc.ProcCoordinator.Session;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedFireCoordinatorTest {
    final UUID generation = UUID.randomUUID(), target = UUID.randomUUID();
    final Session a = new Session(UUID.randomUUID(), UUID.randomUUID()), b = new Session(UUID.randomUUID(), UUID.randomUUID());
    CombatEncounter encounter;
    OwnedFireCoordinator fire;
    CombatProfile profile = CombatProfile.calibration();
    void setup(double hp) {
        encounter = new CombatEncounter(new TargetState(generation,target,hp,hp,0),"fire-test",profile);
        fire = new OwnedFireCoordinator(encounter,2); fire.activate(a); fire.activate(b);
    }
    ShotContext shot(Session owner, double damage, int flame, int duplex, boolean secondary, long tick) {
        var enchants = new ArrayList<WeaponDefinition.Enchantment>();
        if(flame>0) enchants.add(new WeaponDefinition.Enchantment("flame",flame,WeaponDefinition.EnchantmentKind.ORDINARY));
        if(duplex>0) enchants.add(new WeaponDefinition.Enchantment("duplex",duplex,WeaponDefinition.EnchantmentKind.ULTIMATE));
        var weapon = new WeaponDefinition("test",1,"v1",WeaponDefinition.FiringMode.DRAWN_BOW,damage,List.of(),enchants);
        var stats = new StatSnapshotFactory(StatProfile.calibration()).create("fire-test",weapon,ModifierSources.empty()).snapshot();
        return new ShotContext(generation,UUID.randomUUID(),UUID.randomUUID(),secondary?1:0,secondary?Optional.of(UUID.randomUUID()):Optional.empty(),
                owner.ownerId(),new WeaponIdentity(UUID.randomUUID(),"test",1,"v1"),stats,enchants,profile.mechanic(),tick,new Vector3(0,0,0),new Vector3(0,0,1),CritOutcome.NORMAL,1,1);
    }
    DamageResult hit(Session session,double damage,int flame,int duplex,boolean secondary,long tick) {
        var shot=shot(session,damage,flame,duplex,secondary,tick);
        var result=encounter.physical(shot,new PhysicalImpact(new PhysicalImpact.Key(generation,shot.projectileId(),target,0),session.ownerId(),tick,new Vector3(0,0,0),Optional.empty()),DamageModifiers.none(),0,Optional.empty());
        fire.physical(shot,result,tick,session);return result;
    }
    @Test void quiverEveryLevelUsesOneStrictCapturedSampleAndZeroUsesNone() {
        assertFalse(QuiverFlameProfile.capture(0,()->{throw new AssertionError();}).saved());
        for(int level=1;level<=10;level++) {
            double threshold=level*.05;
            assertTrue(QuiverFlameProfile.capture(level,()->Math.nextDown(threshold)).saved());
            assertFalse(QuiverFlameProfile.capture(level,()->threshold).saved());
            assertFalse(QuiverFlameProfile.capture(level,()->Math.nextDown(1d)).saved());
        }
        for(double bad:new double[]{-1,1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->QuiverFlameProfile.capture(10,()->bad));
        assertThrows(IllegalArgumentException.class,()->QuiverFlameProfile.capture(11,()->0));
    }
    @Test void exactlyThreeTicksIncludingFinalBoundaryWithFullHealthCredit() {
        setup(1000);hit(a,100,2,0,false,0);
        assertTrue(fire.tick(19).isEmpty());
        assertEquals(6,fire.tick(20).getFirst().damage().amounts().actualHealthDamage());
        assertEquals(6,fire.tick(40).getFirst().damage().amounts().contributionDamage());
        assertEquals(6,fire.tick(60).getFirst().damage().amounts().contributionDamage());
        assertTrue(fire.tick(61).isEmpty());assertEquals(0,fire.metrics().burns());
        assertEquals(882,encounter.target().currentHealth());assertEquals(4,encounter.acceptedOrdinal());
    }
    @Test void weakerRefreshKeepsPotencySourceAndCadenceEqualRefreshAdoptsLatest() {
        setup(10000);var original=hit(a,100,2,0,false,0);hit(a,10,1,0,false,10);
        var tick=fire.tick(20).getFirst();assertEquals(original.impactId(),tick.damage().parentImpactId().orElseThrow());assertEquals(6,tick.damage().amounts().contributionDamage());
        var equal=hit(a,200,1,0,false,25);
        tick=fire.tick(40).getFirst();assertEquals(equal.impactId(),tick.damage().parentImpactId().orElseThrow());assertEquals(6,tick.damage().amounts().contributionDamage());
        assertEquals(1,fire.tick(60).size());assertEquals(1,fire.tick(80).size());assertTrue(fire.tick(85).isEmpty());
    }
    @Test void vulnerabilityLatestOwnerLevelStrongestAcrossOwnersAndExclusiveExpiry() {
        setup(10000);hit(a,1,0,5,true,0);hit(b,1,0,3,true,1);hit(a,1,0,1,true,2);
        hit(a,100,2,0,false,3);assertEquals(7.8,fire.tick(23).getFirst().damage().amounts().contributionDamage(),1e-10);
        fire.clearSession(b);assertEquals(6.6,fire.tick(43).getFirst().damage().amounts().contributionDamage(),1e-10);
        fire.tick(63);hit(a,100,2,0,false,1182);
        assertEquals(6,fire.tick(1202).getFirst().damage().amounts().contributionDamage());
    }
    @Test void twoOwnersStaySeparateAndSimultaneousLethalUsesCreationOrder() {
        setup(207);hit(b,100,2,0,false,0);hit(a,100,2,0,false,0);
        var results=fire.tick(20);assertEquals(2,results.size());assertEquals(b.ownerId(),results.getFirst().damage().ownerId());
        assertEquals(1,results.getLast().damage().amounts().actualHealthDamage());assertEquals(6,results.getLast().damage().amounts().contributionDamage());
        var frozen=encounter.completion().orElseThrow();assertEquals(106,frozen.participants().get(a.ownerId()).contributionDamage());
        assertEquals(0,fire.metrics().burns());assertTrue(fire.tick(40).isEmpty());assertSame(frozen,encounter.completion().orElseThrow());
    }
    @Test void oldSessionsClearOnlyTheirOwnEffectsAndCannotRecreateBurns() {
        setup(10000);hit(a,100,2,5,true,0);hit(b,100,1,2,true,0);fire.clearSession(a);
        hit(a,100,2,5,true,1);assertEquals(1,fire.metrics().burns());assertEquals(1,fire.metrics().vulnerabilities());
        assertEquals(b.ownerId(),fire.tick(20).getFirst().damage().ownerId());
        var fresh=new Session(a.ownerId(),UUID.randomUUID());fire.activate(fresh);fire.clearSession(a);
        assertEquals(2,fire.metrics().sessions());fire.close();assertEquals(new OwnedFireCoordinator.Metrics(0,0,0),fire.metrics());
        assertTrue(fire.tick(40).isEmpty());
    }
    @Test void fireValidatesSourceLevelDuplicatesTimingAndCannotParentFerocity() {
        setup(1000);var parent=hit(a,100,2,0,false,0);
        var command=new FireCommand(UUID.randomUUID(),parent,2,1,20);
        assertThrows(IllegalArgumentException.class,()->encounter.fire(command,19));
        assertThrows(IllegalArgumentException.class,()->encounter.fire(new FireCommand(UUID.randomUUID(),parent,1,1,20),20));
        var result=encounter.fire(command,20);assertTrue(result.accepted());assertFalse(encounter.fire(command,20).accepted());
        assertThrows(IllegalArgumentException.class,()->new FireCommand(UUID.randomUUID(),result,2,1,40));
        var proc=new ProcCommand(UUID.randomUUID(),result.impactId(),result.origin(),a.ownerId(),result.shotId(),22,6,CritOutcome.NORMAL,profile.mechanic(),0);
        assertThrows(IllegalArgumentException.class,()->encounter.proc(proc,22));
        assertEquals(2,encounter.acceptedOrdinal());
    }
    @Test void fireUsesPhysicalCappedCreditWithoutDoubleMitigationOrGhostFraction() {
        profile=CombatProfile.dragonExperiment(new com.kaveenk.onlydragons.domain.MechanicRevision("fire-cap","v1"),0);
        setup(1000);hit(a,24000,2,0,false,0);
        var result=fire.tick(20).getFirst().damage();
        // Physical 24,000 at H=1000 saturates cap=10; fire starts from 10*.06=.6.
        assertEquals(.6,result.amounts().contributionDamage(),1e-10);assertEquals(.6,result.amounts().requestedHealthDamage(),1e-10);
    }
}
