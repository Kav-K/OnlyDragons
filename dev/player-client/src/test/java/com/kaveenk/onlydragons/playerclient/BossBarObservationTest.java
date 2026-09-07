package com.kaveenk.onlydragons.playerclient;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.data.game.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure received-packet tests for bounded UI snapshots; no rendered appearance or production health calculation is claimed. */
class BossBarObservationTest {
    /** Builds a full red bar with a stable supplied UUID for subsequent packet updates. */
    ClientboundBossEventPacket add(UUID id){return new ClientboundBossEventPacket(id,Component.text("Dragon"),1,BossBarColor.RED,BossBarDivision.NONE,false,false,false);}
    /** Proves old marker snapshots retain their title/health after later updates and removal of the same UUID. */
    @Test void receivesIdentityUpdatesAndRemovalAndRetainsEarlierSnapshots(){
        var probe=new BossBarObservation();UUID id=UUID.randomUUID();probe.receive(add(id));probe.sample("full");
        probe.receive(new ClientboundBossEventPacket(id,.4f));probe.receive(new ClientboundBossEventPacket(id,Component.text("400 / 1000")));probe.sample("damaged");
        probe.receive(new ClientboundBossEventPacket(id));probe.sample("gone");
        var samples=(List<Map<String,Object>>)probe.report().get("samples");
        var full=(List<Map<String,Object>>)samples.get(0).get("bars");var damaged=(List<Map<String,Object>>)samples.get(1).get("bars");
        assertEquals(1f,full.getFirst().get("percent"));assertEquals(.4f,damaged.getFirst().get("percent"));
        assertEquals(id.toString(),damaged.getFirst().get("id"));assertEquals("400 / 1000",damaged.getFirst().get("title"));
        assertEquals(List.of(),samples.get(2).get("bars"));
    }
    /** Fails on ambiguous bar identity, duplicate sample markers and nonfinite received health. */
    @Test void rejectsDuplicateAndUnknownPacketsAndDuplicateMarkers(){
        var probe=new BossBarObservation();UUID id=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->probe.receive(new ClientboundBossEventPacket(id,.5f)));
        probe.receive(add(id));assertThrows(IllegalStateException.class,()->probe.receive(add(id)));
        probe.sample("first");assertThrows(IllegalStateException.class,()->probe.sample("first"));
        assertThrows(IllegalStateException.class,()->probe.receive(new ClientboundBossEventPacket(id,Float.NaN)));
    }
}
