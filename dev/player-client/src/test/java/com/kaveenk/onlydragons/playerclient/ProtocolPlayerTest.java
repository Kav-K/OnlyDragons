package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.junit.jupiter.api.Test;

class ProtocolPlayerTest {
    @Test void capturesReceivedNestedTextButExcludesControlsAndOverlays() {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        assertNull(actor.captureChat(Component.text("ferocity: ").append(Component.text("raw=3.0 effective=3.0")), false));
        assertEquals("select", actor.captureChat(Component.text("OD_PLAYER:" + "a".repeat(32) + ":select"), false));
        assertNull(actor.captureChat(Component.text("OD_PLAYER:" + "b".repeat(32) + ":draw"), false));
        assertNull(actor.captureChat(Component.text("unrelated overlay"), true));
        assertEquals(List.of("ferocity: raw=3.0 effective=3.0"), actor.capturedMessages());
        assertThrows(UnsupportedOperationException.class, () -> actor.capturedMessages().add("forged"));
    }

    @Test void captureFailsClosedOnCountAndNestedLengthOverflow() {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        for (int i = 0; i < ProtocolPlayer.MAX_MESSAGES; i++) actor.captureChat(Component.text("message " + i), false);
        assertThrows(IllegalStateException.class, () -> actor.captureChat(Component.text("overflow"), false));
        var bounded = new ProtocolPlayer("b".repeat(32), "calibrate");
        assertThrows(IllegalStateException.class, () -> bounded.captureChat(Component.text("x".repeat(ProtocolPlayer.MAX_MESSAGE_LENGTH))
                .append(Component.text("overflow")), false));
        assertEquals(List.of(), bounded.capturedMessages());
    }
    @Test void dependencyReallyProvidesPinnedProtocol() {
        assertEquals(ProtocolPlayer.EXPECTED_PROTOCOL, MinecraftCodec.CODEC.getProtocolVersion());
        assertEquals(ProtocolPlayer.EXPECTED_MINECRAFT, MinecraftCodec.CODEC.getMinecraftVersion());
    }

    @Test void syntheticNameFitsMinecraftAndCannotContainUserSuppliedHostOrAccount() {
        assertEquals("od_0123456789abc", ProtocolPlayer.username("0123456789abcdef0123456789abcdef"));
        for (String invalid : new String[] {"Kav-K", "../account", "a".repeat(31), "A".repeat(32)}) {
            assertThrows(IllegalArgumentException.class, () -> ProtocolPlayer.username(invalid));
        }
    }
}
