package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatProvenanceTest {
    final UUID generation = UUID.randomUUID(), target = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    final CombatProfile profile = CombatProfile.calibration();
    CombatEncounter encounter(double hp) { return new CombatEncounter(new TargetState(generation, target, hp, hp, 0), "dummy", profile); }
    ShotContext shot(UUID owner, double damage) {
        var stats = new StatResolver(StatProfile.calibration()).resolve("captured", Map.of(StatKey.WEAPON_DAMAGE, damage), ModifierSources.empty()).snapshot();
        return new ShotContext(generation, UUID.randomUUID(), UUID.randomUUID(), 0, Optional.empty(), owner,
                new WeaponIdentity(UUID.randomUUID(), "ordinary", 1, "v1"), stats, List.of(), profile.mechanic(), 0,
                new Vector3(0,0,0), new Vector3(0,0,1), CritOutcome.NORMAL, 1, 1);
    }
    DamageResult hit(CombatEncounter c, ShotContext s, long tick, DamageModifiers modifiers) {
        return c.physical(s, new PhysicalImpact(new PhysicalImpact.Key(generation, s.projectileId(), target, 0), s.ownerId(), tick,
                new Vector3(0,0,8), Optional.empty()), modifiers, 0, Optional.empty());
    }
    DamageResult hit(CombatEncounter c, UUID owner, double damage, long tick) { return hit(c, shot(owner,damage),tick,DamageModifiers.none()); }
    @Test void sameTickInterleavingZeroToPositiveAndLethalFreezeOneGlobalOrder() {
        var c = encounter(250);
        hit(c,a,0,1); hit(c,b,0,1); hit(c,a,0,2);
        var first = c.contributions().get(a).firstParticipation().orElseThrow();
        assertEquals(new EncounterResult.CommitStamp(1,1), first);
        assertTrue(c.contributions().get(a).lastCreditIncrease().isEmpty());
        hit(c,b,100,3); hit(c,a,100,3); var lethal = hit(c,b,100,3);
        var r = c.completion().orElseThrow();
        assertEquals(6,r.completedOrdinal()); assertEquals(lethal.impactId(),r.completionId());
        assertEquals(50,lethal.amounts().actualHealthDamage()); assertEquals(100,lethal.amounts().contributionDamage());
        assertEquals(first,r.participants().get(a).firstParticipation().orElseThrow());
        assertEquals(new EncounterResult.CommitStamp(3,5),r.participants().get(a).lastCreditIncrease().orElseThrow());
        assertEquals(new EncounterResult.CommitStamp(3,6),r.participants().get(b).lastCreditIncrease().orElseThrow());
        assertFalse(hit(c,a,100,4).accepted()); assertSame(r,c.completion().orElseThrow()); assertEquals(6,c.acceptedOrdinal());
    }
    @Test void roundedAwayAndRepeatedZerosConsumeOrdinalsWithoutImprovingStamp() {
        var c = encounter(Double.MAX_VALUE);
        hit(c,shot(a,1),10,new DamageModifiers(Map.of(),Map.of("large",1e30)));
        var stamp = c.contributions().get(a).lastCreditIncrease();
        hit(c,a,1,11); hit(c,a,0,12);
        assertEquals(3,c.acceptedOrdinal()); assertEquals(1e30,c.contributions().get(a).contributionDamage());
        assertEquals(stamp,c.contributions().get(a).lastCreditIncrease());
    }
    @Test void backdatedPhysicalAndProcDoNotClaimCreateParticipantOrAdvanceOrdinal() {
        var c = encounter(1000); var parent = hit(c,a,100,10); var old = shot(b,100);
        assertThrows(IllegalArgumentException.class,()->hit(c,old,9,DamageModifiers.none()));
        assertFalse(c.contributions().containsKey(b)); assertEquals(1,c.acceptedOrdinal());
        var child = new ProcCommand(UUID.randomUUID(),parent.impactId(),parent.origin(),a,parent.shotId(),10,100,CritOutcome.NORMAL,profile.mechanic(),0);
        hit(c,b,0,12);
        assertThrows(IllegalArgumentException.class,()->c.proc(child,11));
        assertTrue(c.proc(child,12).accepted()); assertTrue(hit(c,old,12,DamageModifiers.none()).accepted());
        assertEquals(4,c.acceptedOrdinal());
    }
    @Test void numericAndCounterOverflowAreAtomicIncludingFailedFirstParticipant() throws Exception {
        var c = encounter(Double.MAX_VALUE); var s = shot(a,1);
        var large = new DamageModifiers(Map.of(),Map.of("large",1e308));
        var first = hit(c,s,10,large); var before = c.contributions(); var health = c.target();
        var failing = shot(a,1); assertThrows(IllegalArgumentException.class,()->hit(c,failing,11,large));
        assertEquals(before,c.contributions()); assertSame(health,c.target()); assertEquals(1,c.acceptedOrdinal());
        assertTrue(c.stamp(first.impactId()).isPresent());
        // Exhaust the private counter as a pure domain boundary fixture; no Paper reflection.
        var counter = CombatEncounter.class.getDeclaredField("acceptedOrdinal"); counter.setAccessible(true); counter.setLong(c,Long.MAX_VALUE);
        var newPlayer = shot(b,0);
        assertThrows(ArithmeticException.class,()->hit(c,newPlayer,12,DamageModifiers.none()));
        assertFalse(c.contributions().containsKey(b)); assertSame(health,c.target()); assertEquals(before,c.contributions());
        assertEquals(1,c.impacts().size());
    }
    @Test void duplicateAndRejectedFirstHitsLeaveProvenanceUntouched() {
        var c=encounter(1000); var s=shot(a,100); hit(c,s,10,DamageModifiers.none());
        var before=c.contributions(); assertFalse(hit(c,s,11,DamageModifiers.none()).accepted());
        var rejected=shot(b,100);
        var r=c.physical(rejected,new PhysicalImpact(new PhysicalImpact.Key(generation,rejected.projectileId(),target,0),b,12,
                new Vector3(0,0,1),Optional.empty()),DamageModifiers.none(),0,Optional.of(DamageResult.RejectionReason.CANCELLED));
        assertFalse(r.accepted()); assertEquals(before,c.contributions()); assertEquals(1,c.acceptedOrdinal());
        c.end(); assertFalse(hit(c,b,100,13).accepted()); assertTrue(c.completion().isEmpty());
    }
    @Test void completionRetainsFullSelectedDefinitionAndRejectsMismatchedIdentity() {
        var selection=com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalogLoader.calibration(CalibrationLoadouts.registry()).bundled().select("test_dragon");
        var c=new CombatEncounter(new TargetState(generation,target,1000,1000,0),"test_dragon",profile,Optional.of(selection));
        hit(c,a,1000,10);var result=c.completion().orElseThrow();
        assertSame(selection,result.selection().orElseThrow());assertEquals(1,result.completedOrdinal());
        assertThrows(IllegalArgumentException.class,()->new CombatEncounter(new TargetState(generation,target,999,999,0),"test_dragon",profile,Optional.of(selection)));
    }

    @Test void publicResultsRejectPartialAndOutOfWindowProvenanceButPreserveLegacyValues() {
        var stamp=Optional.of(new EncounterResult.CommitStamp(10,2));
        var empty=Optional.<EncounterResult.CommitStamp>empty();
        assertThrows(IllegalArgumentException.class,()->new EncounterResult.Contribution(1,1,0,true,stamp,empty));
        assertThrows(IllegalArgumentException.class,()->new EncounterResult.Contribution(1,1,0,false,stamp,stamp));
        assertThrows(IllegalArgumentException.class,()->new EncounterResult.Contribution(0,0,0,false,stamp,empty));
        assertThrows(IllegalArgumentException.class,()->new EncounterResult.Contribution(1,1,0,true,empty,stamp));
        var valid=new EncounterResult.Contribution(1,1,0,true,stamp,stamp);
        assertDoesNotThrow(()->new EncounterResult(UUID.randomUUID(),generation,"dummy",profile.mechanic(),10,Map.of(a,valid),2,Optional.empty()));
        for (long[] boundary : List.of(new long[]{9,2},new long[]{10,1},new long[]{10,0}))
            assertThrows(IllegalArgumentException.class,()->new EncounterResult(UUID.randomUUID(),generation,"dummy",profile.mechanic(),boundary[0],Map.of(a,valid),boundary[1],Optional.empty()));
        assertDoesNotThrow(()->new EncounterResult.Contribution(0,0,0,true,stamp,empty));
        assertDoesNotThrow(()->new EncounterResult(UUID.randomUUID(),generation,"dummy",profile.mechanic(),0,
                Map.of(a,new EncounterResult.Contribution(1,1,0,true))));
    }

}
