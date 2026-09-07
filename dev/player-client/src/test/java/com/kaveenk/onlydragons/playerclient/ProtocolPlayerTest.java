package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.junit.jupiter.api.Test;

/** Legacy actor regression tests for concurrent disconnect, bounded text and the pinned codec; no real server is started. */
class ProtocolPlayerTest {
    /** Requires the complete calibration sequence to close while an independent I/O callback acquires actor state. */
    @Test void ordinaryQuitCanWaitForConcurrentIoCallbackWithoutLockCycle() throws Exception {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        try (var wire = new CallbackWire(actor)) {
            actor.packetReceived(wire.session, offlineLogin());
            actor.packetReceived(wire.session, new ClientboundPlayerPositionPacket(1, 0, 100, 0, 0, 0, 0, 0, 0));
            for (String action : List.of("select", "draw", "release", "quit")) {
                actor.packetReceived(wire.session, new ClientboundSystemChatPacket(Component.text("OD_PLAYER:" + "a".repeat(32) + ":" + action), false));
            }
            assertEquals(List.of("ServerboundAcceptTeleportationPacket", "ServerboundPlayerLoadedPacket",
                    "ServerboundSetCarriedItemPacket", "ServerboundUseItemPacket", "ServerboundPlayerActionPacket"), wire.sent);
            assertEquals(1, wire.completedCallbacks);
            assertEquals(true, actor.report(System.currentTimeMillis()).get("passed"));
        }
    }

    /** Checks deliberate early exit remains a failed, disconnected receipt without a monitor/I/O deadlock. */
    @Test void earlyExitCanWaitForConcurrentIoCallbackWithoutLockCycle() throws Exception {
        var actor = new ProtocolPlayer("a".repeat(32), "early-exit");
        try (var wire = new CallbackWire(actor)) {
            actor.packetReceived(wire.session, offlineLogin());
            assertEquals(1, wire.completedCallbacks);
            assertEquals(List.of(), wire.sent);
            var report = actor.report(System.currentTimeMillis());
            assertEquals(true, report.get("disconnected"));
            assertEquals(false, report.get("passed"));
            assertEquals("Deliberate early client exit", report.get("error"));
        }
    }

    /** Exercises the same concurrent cleanup path for an out-of-order control marker. */
    @Test void invalidControlDisconnectAlsoAllowsConcurrentIoCallback() throws Exception {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        try (var wire = new CallbackWire(actor)) {
            actor.packetReceived(wire.session, new ClientboundSystemChatPacket(Component.text("OD_PLAYER:" + "a".repeat(32) + ":quit"), false));
            assertEquals(1, wire.completedCallbacks);
            assertEquals(false, actor.report(System.currentTimeMillis()).get("passed"));
            assertEquals("Unexpected player action: quit", actor.report(System.currentTimeMillis()).get("error"));
        }
    }

    /** Creates a synthetic offline survival login for the fixed calibration sequence. */
    private static ClientboundLoginPacket offlineLogin() {
        var world = Key.key("minecraft:overworld");
        return new ClientboundLoginPacket(1, false, new Key[] {world}, 1, 2, 2, false, true, false,
                new PlayerSpawnInfo(0, world, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, true, null, 0, 63), false, false);
    }

    /** Models disconnect's blocking I/O close while another I/O callback must complete. */
    private static final class CallbackWire implements AutoCloseable {
        private final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();
        final List<String> sent = new ArrayList<>();
        final Session session;
        int completedCallbacks;

        /** Creates a proxy whose blocking disconnect requires another thread to finish an actor callback. */
        CallbackWire(ProtocolPlayer actor) {
            session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    assertFalse(Thread.holdsLock(actor), "Sending must not hold actor state monitor");
                    sent.add(args[0].getClass().getSimpleName());
                    return null;
                }
                if (method.getName().equals("disconnect")) {
                    var session = (Session) proxy;
                    var reason = (Component) args[0];
                    // No sleeps: the close cannot return until this independent callback
                    // acquires/releases actor state. The former synchronized caller deadlocks.
                    io.submit(() -> {
                        actor.packetReceived(session, new ClientboundKeepAlivePacket(42));
                        actor.disconnected(new DisconnectedEvent(session, reason, null));
                    }).get(5, TimeUnit.SECONDS);
                    completedCallbacks++;
                    return null;
                }
                throw new AssertionError("Unexpected network operation: " + method.getName());
            });
        }

        /** Stops the test-owned I/O executor and fails if concurrent callbacks cannot terminate within the bound. */
        @Override public void close() throws Exception {
            io.shutdownNow();
            assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /** Keeps ordinary nested text separate from control markers/overlays and returns an immutable capture. */
    @Test void capturesReceivedNestedTextButExcludesControlsAndOverlays() {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        assertNull(actor.captureChat(Component.text("ferocity: ").append(Component.text("raw=3.0 effective=3.0")), false));
        assertEquals("select", actor.captureChat(Component.text("OD_PLAYER:" + "a".repeat(32) + ":select"), false));
        assertNull(actor.captureChat(Component.text("OD_PLAYER:" + "b".repeat(32) + ":draw"), false));
        assertNull(actor.captureChat(Component.text("unrelated overlay"), true));
        assertEquals(List.of("ferocity: raw=3.0 effective=3.0"), actor.capturedMessages());
        assertThrows(UnsupportedOperationException.class, () -> actor.capturedMessages().add("forged"));
    }

    /** Rejects excess messages and nested text overflow instead of silently truncating evidence. */
    @Test void captureFailsClosedOnCountAndNestedLengthOverflow() {
        var actor = new ProtocolPlayer("a".repeat(32), "calibrate");
        for (int i = 0; i < ProtocolPlayer.MAX_MESSAGES; i++) actor.captureChat(Component.text("message " + i), false);
        assertThrows(IllegalStateException.class, () -> actor.captureChat(Component.text("overflow"), false));
        var bounded = new ProtocolPlayer("b".repeat(32), "calibrate");
        assertThrows(IllegalStateException.class, () -> bounded.captureChat(Component.text("x".repeat(ProtocolPlayer.MAX_MESSAGE_LENGTH))
                .append(Component.text("overflow")), false));
        assertEquals(List.of(), bounded.capturedMessages());
    }
    /** Checks the resolved codec against generated build pins rather than assuming a dependency name establishes compatibility. */
    @Test void dependencyReallyProvidesPinnedProtocol() {
        assertEquals(ProtocolPlayer.EXPECTED_PROTOCOL, MinecraftCodec.CODEC.getProtocolVersion());
        assertEquals(ProtocolPlayer.EXPECTED_MINECRAFT, MinecraftCodec.CODEC.getMinecraftVersion());
    }

    /** Checks exact run-derived naming and rejects strings that are not admitted lowercase run IDs. */
    @Test void syntheticNameFitsMinecraftAndCannotContainUserSuppliedHostOrAccount() {
        assertEquals("od_0123456789abc", ProtocolPlayer.username("0123456789abcdef0123456789abcdef"));
        for (String invalid : new String[] {"Kav-K", "../account", "a".repeat(31), "A".repeat(32)}) {
            assertThrows(IllegalArgumentException.class, () -> ProtocolPlayer.username(invalid));
        }
    }
}
