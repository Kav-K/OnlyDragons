package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.junit.jupiter.api.Test;

class EntityMotionObservationTest {
    private static ClientboundAddEntityPacket spawn(int id,UUID uuid,EntityType type){
        return new ClientboundAddEntityPacket(id,uuid,type,0,100,0,0,0,0);
    }
    @Test void absoluteRelativeAndTeleportPacketsProduceMeasuredPath(){
        var observation=new EntityMotionObservation();UUID id=UUID.randomUUID();observation.receive(spawn(7,id,EntityType.ENDER_DRAGON));
        observation.receive(new ClientboundMoveEntityPosPacket(7,.25,0,0,false));
        observation.receive(new ClientboundEntityPositionSyncPacket(7,Vector3d.from(.5,100,0),Vector3d.ZERO,0,0,false));
        observation.receive(new ClientboundTeleportEntityPacket(7,Vector3d.from(.25,100,0),Vector3d.ZERO,0,0,List.of(PositionElement.X),false));
        var row=observation.report().getFirst();assertEquals(id.toString(),row.get("uuid"));assertEquals(3,row.get("packets"));
        assertEquals(List.of(.75,100.0,0.0),row.get("last"));assertEquals(.75,row.get("path"));assertEquals(.25,row.get("maxStep"));
    }
    @Test void removalAndReusedNetworkIdDoNotConflateEntityIdentity(){
        var observation=new EntityMotionObservation();UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        observation.receive(spawn(7,first,EntityType.ENDER_DRAGON));observation.receive(new ClientboundRemoveEntitiesPacket(new int[]{7}));
        observation.receive(new ClientboundMoveEntityPosPacket(7,20,0,0,false));
        observation.receive(spawn(7,UUID.randomUUID(),EntityType.ZOMBIE));observation.receive(new ClientboundMoveEntityPosPacket(7,20,0,0,false));
        observation.receive(spawn(7,second,EntityType.ENDER_DRAGON));observation.receive(new ClientboundMoveEntityPosPacket(7,.2,0,0,false));
        var rows=observation.report();assertEquals(2,rows.size());assertEquals(0,rows.getFirst().get("packets"));
        assertEquals(second.toString(),rows.getLast().get("uuid"));assertEquals(.2,rows.getLast().get("path"));
    }
    @Test void nonfiniteMotionAndSegmentOverflowFailClosed(){
        var observation=new EntityMotionObservation();observation.receive(spawn(7,UUID.randomUUID(),EntityType.ENDER_DRAGON));
        assertThrows(IllegalArgumentException.class,()->observation.receive(new ClientboundMoveEntityPosPacket(7,Double.NaN,0,0,false)));
        for(int i=1;i<128;i++)observation.receive(spawn(i,UUID.randomUUID(),EntityType.ENDER_DRAGON));
        assertThrows(IllegalStateException.class,()->observation.receive(spawn(200,UUID.randomUUID(),EntityType.ENDER_DRAGON)));
    }
}
