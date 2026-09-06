package com.kaveenk.onlydragons.application.proc;

import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TempoGhostPolicyTest {
    final UUID id=UUID.randomUUID(), target=UUID.randomUUID(), owner=UUID.randomUUID();
    final ProcCoordinator.Session session=new ProcCoordinator.Session(owner,UUID.randomUUID());
    final CombatProfile profile=CombatProfile.tempoDragon();
    CombatEncounter encounter=new CombatEncounter(new TargetState(id,target,100_000,100_000,0),"test",profile);
    ProcCoordinator queue(int spacing) {
        var q=new ProcCoordinator(encounter,new ProcCoordinator.Limits(100,100,2,spacing),()->.99);
        q.activate(session);return q;
    }
    ShotContext shot(double f,int ft,boolean duplex) {
        var stats=new StatResolver(StatProfile.calibration()).resolve("test",Map.of(StatKey.WEAPON_DAMAGE,100.0,StatKey.FEROCITY,f),ModifierSources.empty()).snapshot();
        var enchants=ft>0?List.of(new WeaponDefinition.Enchantment("fatal_tempo",ft,WeaponDefinition.EnchantmentKind.ULTIMATE))
                :duplex?List.of(new WeaponDefinition.Enchantment("duplex",5,WeaponDefinition.EnchantmentKind.ULTIMATE)):List.<WeaponDefinition.Enchantment>of();
        return new ShotContext(id,UUID.randomUUID(),UUID.randomUUID(),0,duplex?Optional.of(UUID.randomUUID()):Optional.empty(),owner,
                new WeaponIdentity(UUID.randomUUID(),"test",1,"v1"),stats,enchants,profile.mechanic(),0,new Vector3(0,0,0),new Vector3(1,0,0),CritOutcome.NORMAL,1,duplex?.2:1);
    }
    ProcCoordinator.PhysicalResult hit(ProcCoordinator q,ShotContext s,long tick) {
        return q.physical(s,new PhysicalImpact(new PhysicalImpact.Key(id,s.projectileId(),target,0),owner,tick,new Vector3(1,0,0),Optional.empty()),DamageModifiers.none(),session,Optional.empty());
    }
    @ParameterizedTest @CsvSource({"0,1.0","1,.9","2,.8","3,.7","4,.6","5,.5"})
    void allLevelsUsePreHitSharedPolicyAndFullCredit(int level,double fraction) {
        var q=queue(2);
        if(level>0)hit(q,shot(0,level,false),10);
        var physical=hit(q,shot(100,0,true),11);
        var command=physical.children().getFirst();
        assertEquals(0,command.fatalTempoSourceLevel());
        assertEquals(level,command.healthSnapshot().orElseThrow().activeSourceLevel());
        assertEquals(fraction,command.healthSnapshot().orElseThrow().healthFraction());
        var proc=q.tick(13).getFirst();
        assertEquals(20*fraction,proc.amounts().actualHealthDamage());
        assertEquals(20,proc.amounts().contributionDamage());
        assertEquals(level*10,q.tempoBonus(session,13));
        assertEquals(0,q.metrics().queued());
    }
    @Test void mixedLevelsUseLatestIncludingChildrenAndFreezeBeforeNewHit() {
        var q=queue(2);
        hit(q,shot(0,5,false),10);
        var lower=hit(q,shot(100,1,false),11);
        assertEquals(5,lower.children().getFirst().healthSnapshot().orElseThrow().activeSourceLevel());
        assertEquals(1,lower.children().getFirst().fatalTempoSourceLevel());
        var ordinary=hit(q,shot(100,0,false),12);
        assertEquals(1,ordinary.children().getFirst().healthSnapshot().orElseThrow().activeSourceLevel());
        hit(q,shot(0,4,false),12);
        var child=q.tick(13).getFirst();
        assertEquals(50,child.amounts().actualHealthDamage());
        var following=hit(q,shot(100,0,false),13);
        assertEquals(1,following.children().getFirst().healthSnapshot().orElseThrow().activeSourceLevel());
        assertEquals(110,following.children().getFirst().healthSnapshot().orElseThrow().activeBonusPercent());
    }
    @Test void queuedChildrenSurviveExpiryWithFrozenHpButDoNotRefreshWithoutAncestry() {
        var q=queue(70);hit(q,shot(0,5,false),10);
        var old=hit(q,shot(100,0,true),11).children().getFirst();
        assertEquals(.5,old.healthSnapshot().orElseThrow().healthFraction());
        assertEquals(50,q.tempoBonus(session,69));assertEquals(0,q.tempoBonus(session,70));
        var expired=hit(q,shot(100,1,false),70);
        assertEquals(100,expired.damage().effectiveFerocity());
        assertEquals(0,expired.children().getFirst().healthSnapshot().orElseThrow().activeSourceLevel());
        var late=q.tick(81).getFirst();assertEquals(10,late.amounts().actualHealthDamage());assertEquals(20,late.amounts().contributionDamage());
        assertEquals(10,q.tempoBonus(session,81));assertEquals(0,q.tempoBonus(session,130));
        var ftChild=q.tick(140).getFirst();assertEquals(100,ftChild.amounts().actualHealthDamage());
        assertEquals(10,q.tempoBonus(session,140));assertEquals(0,q.metrics().queued());
        assertEquals(.5,old.healthSnapshot().orElseThrow().healthFraction());
    }
    @ParameterizedTest @CsvSource({"0,0","25,0","99,0","100,1","101,1","250,2","499,4","500,5","900,5"})
    void independentProbabilityAndCapArePreserved(double base,int count) {
        var q=queue(2);var result=hit(q,shot(base,0,false),10);
        assertEquals(count,result.children().size());assertEquals(Math.min(500,base),result.damage().effectiveFerocity());
    }
    @Test void reconnectResetAndLethalCannotAdmitLateGhostCredit() {
        var q=queue(2);hit(q,shot(100,5,false),10);
        var fresh=new ProcCoordinator.Session(owner,UUID.randomUUID());q.activate(fresh);q.clearSession(session);
        assertEquals(0,q.metrics().queued());assertEquals(0,q.tempoBonus(fresh,10));
        assertEquals(ProcCoordinator.Admission.INACTIVE_SESSION,hit(q,shot(100,5,false),11).admission());
        q.close();assertEquals(0,q.metrics().sessions());var closed=q;assertThrows(IllegalStateException.class,()->closed.tick(20));
        encounter=new CombatEncounter(new TargetState(id,target,100,100,0),"test",profile);
        q=queue(2);hit(q,shot(500,5,false),10);var frozen=encounter.completion().orElseThrow();
        assertTrue(q.tick(20).stream().allMatch(r->r.rejectionReason().equals(Optional.of(DamageResult.RejectionReason.TARGET_DEAD))));
        assertEquals(frozen,encounter.completion().orElseThrow());assertEquals(100,frozen.participants().get(owner).contributionDamage());
    }
    @Test void missingOrRewrittenPolicyRejectsWithoutPartialCredit() {
        var q=queue(2);var original=hit(q,shot(100,0,false),10).children().getFirst();
        var missing=new ProcCommand(original.procId(),original.parentImpactId(),original.origin(),owner,original.shotId(),12,100,CritOutcome.NORMAL,profile.mechanic(),0);
        assertThrows(IllegalArgumentException.class,()->encounter.proc(missing,12));
        var forged=new ProcCommand(original.procId(),original.parentImpactId(),original.origin(),owner,original.shotId(),12,100,CritOutcome.NORMAL,profile.mechanic(),0,
                Optional.of(new ProcHealthSnapshot(50,5,70,.5,100)));
        assertThrows(IllegalArgumentException.class,()->encounter.proc(forged,12));
        assertEquals(1,encounter.acceptedOrdinal());assertEquals(100,q.tick(12).getFirst().amounts().actualHealthDamage());
    }
}
