package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.playerclient.ActionPlanTest.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.*;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;
import org.junit.jupiter.api.Test;

/** Pure protocol-state tests using the pinned packet codec and a synthetic wire; Paper-side effects require separate scenarios. */
class ActionSessionTest {
    static final String RUN = "a".repeat(32);
    /** Provides a synthetic offline survival login for protocol-state tests. */
    static ClientboundLoginPacket login() {
        var world = Key.key("minecraft:overworld");
        return new ClientboundLoginPacket(1, false, new Key[]{world}, 4, 2, 2, false, true, false,
                new PlayerSpawnInfo(0, world, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, true, null, 0, 63), false, false);
    }
    /** Provides the initial teleport whose acknowledgement establishes loaded fixture state. */
    static ClientboundPlayerPositionPacket position() { return new ClientboundPlayerPositionPacket(1, .5, 100, .5, 0, 0, 0, 0, 0); }
    /** Builds a control marker bound to this test run, exact plan digest, actor, session and step. */
    static String marker(ActionPlan plan, String actor, String session, String step) { return "OD_ACTION:" + RUN + ":" + plan.sha256() + ":" + actor + ":" + session + ":" + step; }
    /** Wraps text as ordinary system chat rather than an action-bar overlay. */
    static ClientboundSystemChatPacket chat(String text) { return new ClientboundSystemChatPacket(Component.text(text), false); }
    /** Creates the first test connection with inert lifecycle callbacks; tests inspect session state directly. */
    static ActionSession session(ActionPlan plan) {
        return new ActionSession(RUN, "alpha", "calibrate", plan, plan.actors().getFirst().sessions().getFirst(), new ActionSession.Owner() {
            /** No-op owner: the test reads terminal state without starting cohort lifecycle work. */
            public void ended(ActionSession ignored) {}
            /** No-op owner: failure remains visible on the session under test. */
            public void failed(String ignored) {}
        });
    }
    /** Captures outgoing packets and makes disconnect await a concurrent callback to expose monitor/I/O lock cycles. */
    static final class Wire implements AutoCloseable {
        final List<Packet> packets = new ArrayList<>();
        final ExecutorService io = Executors.newSingleThreadExecutor();
        final Session session;
        /** Creates a socket-free transport proxy that rejects network operations under the actor monitor. */
        Wire(ActionSession actor) {
            session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class[]{Session.class}, (proxy, method, args) -> {
                assertFalse(Thread.holdsLock(actor), "Network calls must occur outside actor monitor");
                if (method.getName().equals("send")) { packets.add((Packet) args[0]); return null; }
                if (method.getName().equals("disconnect")) {
                    io.submit(() -> {
                        actor.packetReceived((Session) proxy, new ClientboundKeepAlivePacket(42));
                        actor.disconnected(new DisconnectedEvent((Session) proxy, (Component) args[0], null));
                    }).get(3, TimeUnit.SECONDS);
                    return null;
                }
                throw new AssertionError(method.getName());
            });
        }
        /** Delivers login and teleport through the actual session callback entry point. */
        void load(ActionSession actor) { actor.packetReceived(session, login()); actor.packetReceived(session, position()); }
        /** Stops and joins the test I/O executor so a deadlocked callback cannot silently escape the test. */
        public void close() throws Exception { io.shutdownNow(); assertTrue(io.awaitTermination(3, TimeUnit.SECONDS)); }
    }

    /** Exercises each declared primitive through marker handling and round-trips outgoing packets with the pinned codec. */
    @Test void actualPinnedPacketFieldsAndWireBytesMatchDeclaredPrimitives() throws Exception {
        String actions = String.join(",",
                step("select", "selectSlot", "{\"slot\":2}"),
                step("move", "move", "{\"x\":1.5,\"y\":100,\"z\":0.5,\"onGround\":false}"),
                step("aim", "look", "{\"yaw\":90,\"pitch\":-10}"),
                step("command", "command", "{\"command\":\"onlydragons status\"}"),
                step("use", "useItem", "{\"hand\":\"off\"}"), step("release", "releaseUse", "{}"),
                step("swap", "swapHands", "{}"), step("drop", "dropItem", "{\"all\":false}"),
                step("swing", "swing", "{\"hand\":\"main\"}"),
                step("block", "interactBlock", "{\"x\":1,\"y\":99,\"z\":0,\"face\":\"up\",\"hand\":\"main\",\"cursorX\":0.5,\"cursorY\":1,\"cursorZ\":0.5,\"insideBlock\":false}"),
                step("attack", "attackEntity", "{\"targetRef\":\"target\"}"),
                step("revive", "respawn", "{}"), exit());
        var plan = parse(plan(actions)); var actor = session(plan); UUID target = UUID.randomUUID();
        try (var wire = new Wire(actor)) {
            wire.load(actor);
            actor.packetReceived(wire.session, new ClientboundAddEntityPacket(71, target, EntityType.ZOMBIE, 2, 100, 0, 0, 0, 0));
            actor.packetReceived(wire.session, chat("OD_BIND:" + RUN + ":" + plan.sha256() + ":target:" + target));
            for (var step : plan.actors().getFirst().sessions().getFirst().steps()) {
                if (step.action().equals("respawn")) actor.packetReceived(wire.session, new ClientboundSetHealthPacket(0, 20, 5));
                actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", step.id())));
            }
            assertTrue(actor.successful(), actor.report().toString());
            assertEquals(2, ((ServerboundSetCarriedItemPacket) wire.packets.get(2)).getSlot());
            var move = (ServerboundMovePlayerPosPacket) wire.packets.get(3);
            assertEquals(1.5, move.getX()); assertFalse(move.isOnGround());
            var use = wire.packets.stream().filter(ServerboundUseItemPacket.class::isInstance).map(ServerboundUseItemPacket.class::cast).findFirst().orElseThrow();
            assertEquals(Hand.OFF_HAND, use.getHand()); assertEquals(90, use.getYRot()); assertEquals(-10, use.getXRot());
            var attack = wire.packets.stream().filter(ServerboundAttackPacket.class::isInstance).map(ServerboundAttackPacket.class::cast).findFirst().orElseThrow();
            assertEquals(71, attack.getEntityId());
            // Exercise actual pinned codecs, not a parallel encoder or constructor mirror.
            for (Packet packet : wire.packets) {
                if (!(packet instanceof MinecraftPacket minecraft) || packet.getClass().getSimpleName().equals("ServerboundPlayerLoadedPacket")) continue;
                ByteBuf bytes = Unpooled.buffer();
                try {
                    minecraft.serialize(bytes);
                    Object decoded = packet.getClass().getConstructor(ByteBuf.class).newInstance(bytes);
                    assertEquals(packet, decoded); assertEquals(0, bytes.readableBytes());
                } finally { bytes.release(); }
            }
            @SuppressWarnings("unchecked") var rows = (List<Map<String, Object>>) actor.report().get("steps");
            var row = rows.stream().filter(r -> r.get("action").equals("attackEntity")).findFirst().orElseThrow();
            assertEquals(target.toString(), row.get("targetUuid")); assertEquals(71, row.get("networkEntityId"));
        }
    }

    /** Prevents attacks when binding, observed spawn or still-live network identity is missing. */
    @Test void targetMustBeBoundObservedAndNotRemoved() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            var plan = parse(plan(step("attack", "attackEntity", "{\"targetRef\":\"target\"}") + "," + exit()));
            var actor = session(plan); UUID target = UUID.randomUUID();
            try (var wire = new Wire(actor)) {
                wire.load(actor);
                if (mode > 0) actor.packetReceived(wire.session, chat("OD_BIND:" + RUN + ":" + plan.sha256() + ":target:" + target));
                if (mode == 2) {
                    actor.packetReceived(wire.session, new ClientboundAddEntityPacket(71, target, EntityType.ZOMBIE, 2, 100, 0, 0, 0, 0));
                    actor.packetReceived(wire.session, new ClientboundRemoveEntitiesPacket(new int[]{71}));
                }
                actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "attack")));
                assertFalse(actor.successful()); assertTrue(wire.packets.stream().noneMatch(ServerboundAttackPacket.class::isInstance));
                assertEquals(List.of(), actor.report().get("steps"));
            }
        }
    }
    /** Separates ignored foreign markers from rejected current-actor duplicates and stale-session requests. */
    @Test void markerOrderingAndCrossActorRunSessionIsolationFailClosed() throws Exception {
        var plan = parse(plan(step("select", "selectSlot", "{\"slot\":1}") + "," + exit()));
        var actor = session(plan);
        try (var wire = new Wire(actor)) {
            wire.load(actor);
            actor.packetReceived(wire.session, chat(marker(plan, "beta", "first", "select")));
            actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "select").replace(RUN, "b".repeat(32))));
            assertEquals(List.of(), actor.report().get("steps"));
            actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "select")));
            actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "select")));
            assertFalse(actor.successful()); assertTrue(actor.report().get("error").toString().contains("Unexpected action step"));
        }
        var stale = session(plan);
        try (var wire = new Wire(stale)) {
            wire.load(stale); stale.packetReceived(wire.session, chat(marker(plan, "alpha", "old-session", "select")));
            assertFalse(stale.successful()); assertEquals(List.of(), stale.report().get("steps"));
        }
    }
    /** Rejects actions before loading, respawn before death and movement beyond the per-action displacement bound. */
    @Test void loadingRespawnAndMovementGuardsPreventUnsupportedActions() throws Exception {
        for (String step : List.of(step("action", "respawn", "{}"), step("action", "move", "{\"x\":100,\"y\":100,\"z\":0,\"onGround\":false}"))) {
            var plan = parse(plan(step + "," + exit())); var actor = session(plan);
            try (var wire = new Wire(actor)) {
                wire.load(actor); actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "action")));
                assertFalse(actor.successful()); assertEquals(List.of(), actor.report().get("steps"));
            }
        }
        var plan = parse(plan(exit())); var actor = session(plan);
        try (var wire = new Wire(actor)) {
            actor.packetReceived(wire.session, chat(marker(plan, "alpha", "first", "end")));
            assertFalse(actor.successful()); assertEquals(List.of(), actor.report().get("steps"));
        }
    }
}
