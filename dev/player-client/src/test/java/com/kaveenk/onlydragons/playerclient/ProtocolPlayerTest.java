package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.junit.jupiter.api.Test;

class ProtocolPlayerTest {
    @Test void dependencyReallyProvidesPinnedProtocol() {
        assertEquals(776, MinecraftCodec.CODEC.getProtocolVersion());
        assertEquals("26.2", MinecraftCodec.CODEC.getMinecraftVersion());
    }

    @Test void syntheticNameFitsMinecraftAndCannotContainUserSuppliedHostOrAccount() {
        assertEquals("od_0123456789abc", ProtocolPlayer.username("0123456789abcdef0123456789abcdef"));
        for (String invalid : new String[] {"Kav-K", "../account", "a".repeat(31), "A".repeat(32)}) {
            assertThrows(IllegalArgumentException.class, () -> ProtocolPlayer.username(invalid));
        }
    }
}
