package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.application.LeaderboardMessages;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RankedEncounterResultTest {
    // Reuse the established domain fixture inputs without inheriting/rerunning its tests.
    final CombatProvenanceTest fixture = new CombatProvenanceTest();
    final UUID generation=fixture.generation, target=fixture.target, a=fixture.a, b=fixture.b;
    final CombatProfile profile=fixture.profile;
    CombatEncounter encounter(double hp){return fixture.encounter(hp);}
    ShotContext shot(UUID owner,double damage){return fixture.shot(owner,damage);}
    DamageResult hit(CombatEncounter c,UUID owner,double damage,long tick){return fixture.hit(c,owner,damage,tick);}
    DamageResult hit(CombatEncounter c,ShotContext s,long tick,DamageModifiers modifiers){return fixture.hit(c,s,tick,modifiers);}
    @Test void exactTiesUseLastIncreaseTickThenAcceptedOrdinalNotUuidOrFirstHit() {
        var c=encounter(400);
        hit(c,a,0,0); hit(c,a,50,1); hit(c,b,100,2); hit(c,a,50,3);
        UUID other=UUID.randomUUID();hit(c,other,200,4);
        var ranked=new RankedEncounterResult(c.completion().orElseThrow());
        assertEquals(new EncounterResult.CommitStamp(0,1),ranked.placement(a).orElseThrow().contribution().firstParticipation().orElseThrow());
        assertEquals(List.of(other,b,a),ranked.placements().stream().map(RankedEncounterResult.Placement::playerId).toList());
        var same=encounter(200);hit(same,b,100,5);hit(same,a,100,5);
        assertEquals(b,new RankedEncounterResult(same.completion().orElseThrow()).placements().getFirst().playerId());
    }
    @Test void renderedEqualityDoesNotChangeFullPrecisionOrderAndTopTenIncludesOwnEleventh() {
        var participants=new LinkedHashMap<UUID,EncounterResult.Contribution>();
        for(int i=1;i<=12;i++)participants.put(new UUID(0,i), contribution(1,100+i*.00001,i,i));
        var r=result(participants,12);var ranked=new RankedEncounterResult(r);
        assertSame(r,ranked.result());assertEquals(new UUID(0,12),ranked.placements().getFirst().playerId());
        var lines=LeaderboardMessages.topTen(ranked,id->"same name");
        assertEquals(11,lines.size());assertTrue(lines.getLast().startsWith("#10 "));
        assertTrue(lines.subList(1,11).stream().allMatch(line->line.endsWith("100.00 credited damage")));
        assertEquals("Your placement: #11 - 100.00 credited damage",LeaderboardMessages.own(ranked.placement(new UUID(0,2)).orElseThrow()));
        assertThrows(UnsupportedOperationException.class,()->ranked.placements().clear());
        assertTrue(ranked.placement(UUID.randomUUID()).isEmpty());
    }
    @Test void everyInputPermutationHasSameUniquePlacementsIncludingZeroParticipants() {
        var entries=List.of(Map.entry(a,contribution(0,0,1,2)),Map.entry(b,contribution(0,-0.0,1,1)),
                Map.entry(new UUID(0,1),contribution(1,10,2,3)),Map.entry(new UUID(0,2),contribution(1,10,2,4)));
        List<UUID> expected=List.of(new UUID(0,1),new UUID(0,2),b,a);
        for(var permutation:permutations(entries)) {
            var map=new LinkedHashMap<UUID,EncounterResult.Contribution>();permutation.forEach(e->map.put(e.getKey(),e.getValue()));
            var ranked=new RankedEncounterResult(result(map,4));
            assertEquals(expected,ranked.placements().stream().map(RankedEncounterResult.Placement::playerId).toList());
            assertEquals(List.of(1,2,3,4),ranked.placements().stream().map(RankedEncounterResult.Placement::place).toList());
            assertEquals("Your placement: #3 - 0.00 credited damage",LeaderboardMessages.own(ranked.placement(b).orElseThrow()));
        }
    }
    @Test void realCommitsKeepZeroRejectedReplayRoundedAndLateHitsOutOfTieImprovements() {
        var c=encounter(400); UUID zero=UUID.randomUUID();hit(c,zero,0,1);
        var shot=shot(a,100);hit(c,shot,2,DamageModifiers.none());hit(c,b,100,3);
        hit(c,zero,0,4);hit(c,a,0,5);hit(c,a,1e-20,6);
        assertFalse(hit(c,shot,7,DamageModifiers.none()).accepted());
        var rejected=shot(UUID.randomUUID(),100);
        c.physical(rejected,new PhysicalImpact(new PhysicalImpact.Key(generation,rejected.projectileId(),target,0),rejected.ownerId(),8,new Vector3(0,0,1),Optional.empty()),DamageModifiers.none(),0,Optional.of(DamageResult.RejectionReason.CANCELLED));
        UUID killer=UUID.randomUUID();hit(c,killer,200,9);
        var ranked=new RankedEncounterResult(c.completion().orElseThrow());
        assertEquals(List.of(killer,a,b,zero),ranked.placements().stream().map(RankedEncounterResult.Placement::playerId).toList());
        assertEquals(new EncounterResult.CommitStamp(2,2),ranked.placement(a).orElseThrow().contribution().lastCreditIncrease().orElseThrow());
        assertFalse(hit(c,b,1000,10).accepted());assertEquals(100,ranked.placement(b).orElseThrow().contribution().contributionDamage());
    }
    @Test void reducedAndScoreOnlyProcsPlusLethalOverkillRankCreditSeparatelyFromHp() {
        for(double fraction:List.of(0.0,.25)) {
            var custom=new CombatProfile(profile.mechanic(),CombatProfile.Mitigation.NONE,CombatProfile.Cap.NONE,fraction);
            var c=new CombatEncounter(new TargetState(generation,target,450,450,0),"dummy",custom);
            var parent=hit(c,a,200,1);
            c.proc(new ProcCommand(UUID.randomUUID(),parent.impactId(),parent.origin(),a,parent.shotId(),2,200,CritOutcome.NORMAL,profile.mechanic(),0),2);
            hit(c,b,300,3);
            var ranked=new RankedEncounterResult(c.completion().orElseThrow());
            assertEquals(a,ranked.placements().getFirst().playerId());
            assertEquals(400,ranked.placement(a).orElseThrow().contribution().contributionDamage());
            assertEquals(200+200*fraction,ranked.placement(a).orElseThrow().contribution().actualHealthDamage());
            assertEquals(300,ranked.placement(b).orElseThrow().contribution().contributionDamage());
            assertEquals(250-200*fraction,ranked.placement(b).orElseThrow().contribution().actualHealthDamage());
        }
    }
    @Test void rejectsMissingCrossParticipantAndBackdatedProvenanceWithoutFallback() {
        assertThrows(IllegalArgumentException.class,()->new RankedEncounterResult(result(Map.of(a,new EncounterResult.Contribution(1,1,0,true)),1)));
        assertThrows(IllegalArgumentException.class,()->new RankedEncounterResult(result(Map.of(a,contribution(1,1,1,1),b,contribution(1,1,1,1)),2)));
        assertThrows(IllegalArgumentException.class,()->new RankedEncounterResult(result(Map.of(a,contribution(1,1,2,1),b,contribution(1,1,1,2)),2)));
        var first=Optional.of(new EncounterResult.CommitStamp(1,1));var last=Optional.of(new EncounterResult.CommitStamp(2,1));
        assertThrows(IllegalArgumentException.class,()->new RankedEncounterResult(result(Map.of(a,new EncounterResult.Contribution(1,1,0,true,first,last)),2)));
    }
    static EncounterResult.Contribution contribution(double hp,double credit,long tick,long ordinal) {
        var stamp=Optional.of(new EncounterResult.CommitStamp(tick,ordinal));
        return new EncounterResult.Contribution(hp,credit,0,true,stamp,credit>0?stamp:Optional.empty());
    }
    EncounterResult result(Map<UUID,EncounterResult.Contribution> participants,long ordinal) {
        return new EncounterResult(UUID.randomUUID(),generation,"dummy",profile.mechanic(),participants.values().stream().flatMap(c->c.firstParticipation().stream()).mapToLong(EncounterResult.CommitStamp::tick).max().orElse(0),participants,ordinal,Optional.empty());
    }
    static <T> List<List<T>> permutations(List<T> values) {
        if(values.isEmpty())return List.of(List.of());var result=new ArrayList<List<T>>();
        for(int i=0;i<values.size();i++){var remaining=new ArrayList<>(values);var first=remaining.remove(i);for(var tail:permutations(remaining)){var row=new ArrayList<T>();row.add(first);row.addAll(tail);result.add(row);}}return result;
    }
}
