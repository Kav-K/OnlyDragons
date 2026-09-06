package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.encounter.EncounterResult;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class DragonLeaderboardPresenterTest {
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),generation=UUID.randomUUID();
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    DevelopmentDragonService.Completion completion(UUID generation,String outcome){
        var one=Optional.of(new EncounterResult.CommitStamp(1,1));var two=Optional.of(new EncounterResult.CommitStamp(2,2));
        var r=new EncounterResult(UUID.randomUUID(),generation,"dummy",CombatProfile.calibration().mechanic(),2,
                Map.of(a,new EncounterResult.Contribution(100,200,0,true,one,one),b,new EncounterResult.Contribution(50,100,0,true,two,two)),2,Optional.empty());
        return new DevelopmentDragonService.Completion(generation,UUID.randomUUID(),r,outcome);
    }
    @Test void onceOnlyUuidDeliveryRetainsResultDespiteNameChangesAndReentry(){
        var received=new HashMap<UUID,List<String>>();String[] name={"before"};DragonLeaderboardPresenter[] ref={null};
        var event=completion(generation,"ANIMATING");
        ref[0]=new DragonLeaderboardPresenter(id->name[0],(id,lines)->{assertNull(received.put(id,lines));ref[0].accept(event);});
        var p=ref[0];p.begin(generation);p.accept(event);name[0]="after";p.accept(event);
        assertEquals(Set.of(a,b),received.keySet());assertSame(event.result(),p.ranking().orElseThrow().result());
        assertTrue(received.get(a).getLast().contains("#1 - 200.00"));assertTrue(received.get(b).getLast().contains("#2 - 100.00"));
        assertEquals(0,p.deliveryFailures());assertTrue(received.get(a).get(1).contains("before"));
    }
    @Test void diagnosticRetiredAndStaleGenerationsNeverAnnounce(){
        var received=new ArrayList<UUID>();var p=new DragonLeaderboardPresenter(UUID::toString,(id,lines)->received.add(id));
        var old=completion(generation,"ANIMATING");p.begin(generation);
        for(String outcome:List.of("RESET","DEATH_CANCELLED","NATIVE_MISMATCH","REMOVED_DEATH"))p.accept(completion(generation,outcome));
        assertTrue(received.isEmpty());assertTrue(p.ranking().isEmpty());p.retire(generation);p.accept(old);assertTrue(received.isEmpty());
        UUID fresh=UUID.randomUUID();p.begin(fresh);p.retire(generation);p.accept(old);assertTrue(received.isEmpty());
        p.accept(completion(fresh,"ANIMATING"));assertEquals(2,received.size());
        p.retire(fresh);p.accept(old);assertEquals(2,received.size());
    }
    @Test void recipientFailureCannotPreventOthersOrPermitRetry(){
        var received=new ArrayList<UUID>();var p=new DragonLeaderboardPresenter(UUID::toString,(id,lines)->{if(id.equals(a))throw new IllegalStateException("sink fixture");received.add(id);});
        var event=completion(generation,"ANIMATING");p.begin(generation);p.accept(event);p.accept(event);
        assertEquals(List.of(b),received);assertEquals(1,p.deliveryFailures());assertTrue(p.ranking().isPresent());
    }
}
