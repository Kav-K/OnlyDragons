package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.playerclient.ActionPlanTest.*;
import static com.kaveenk.onlydragons.playerclient.ActionSessionTest.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.junit.jupiter.api.Test;

class ActionPlayerTest {
    static ActionPlan multiple() throws Exception {
        String reconnect = step("again", "reconnect", "{\"delayMillis\":100}");
        String first = "{\"id\":\"first\",\"steps\":[" + reconnect + "]}";
        String second = "{\"id\":\"second\",\"steps\":[" + step("status", "command", "{\"command\":\"onlydragons status\"}") + "," + exit() + "]}";
        return parse("{\"schemaVersion\":1,\"planId\":\"multi\",\"targets\":[],\"actors\":["
                + "{\"id\":\"alpha\",\"sessions\":[" + first + "," + second + "]},"
                + "{\"id\":\"beta\",\"sessions\":[" + first + "," + second + "]}]}" );
    }
    static final class Connections implements ActionPlayer.ConnectionFactory, AutoCloseable {
        final ActionPlan plan;
        final boolean idle;
        final Map<String, Integer> visits = new ConcurrentHashMap<>();
        final List<AtomicBoolean> connected = new CopyOnWriteArrayList<>();
        final List<String> commands = new CopyOnWriteArrayList<>();
        final ExecutorService io = Executors.newSingleThreadExecutor();
        Connections(ActionPlan plan, boolean idle) { this.plan = plan; this.idle = idle; }
        public ClientSession create(String name, ExecutorService callbacks) {
            int index = Integer.parseInt(name.substring(name.length() - 1));
            int visit = visits.merge(name, 1, Integer::sum) - 1;
            var definition = plan.actors().get(index);
            var session = definition.sessions().get(visit);
            AtomicBoolean live = new AtomicBoolean(); connected.add(live);
            ActionSession[] listener = new ActionSession[1];
            return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class[]{ClientSession.class}, (proxy, method, args) -> {
                if (listener[0] != null) assertFalse(Thread.holdsLock(listener[0]));
                switch (method.getName()) {
                    case "setFlag" -> { return null; }
                    case "addListener" -> { listener[0] = (ActionSession) args[0]; return null; }
                    case "isConnected" -> { return live.get(); }
                    case "connect" -> {
                        live.set(true);
                        callbacks.execute(() -> {
                            var actor = listener[0];
                            actor.packetReceived((ClientSession) proxy, login());
                            actor.packetReceived((ClientSession) proxy, position());
                            if (!idle) for (var step : session.steps()) actor.packetReceived((ClientSession) proxy,
                                    chat(marker(plan, definition.id(), session.id(), step.id())));
                        });
                        return null;
                    }
                    case "send" -> { commands.add(name + ":" + args[0].getClass().getSimpleName()); return null; }
                    case "disconnect" -> {
                        if (live.compareAndSet(true, false)) io.submit(() -> listener[0].disconnected(
                                new DisconnectedEvent((ClientSession) proxy, (Component) args[0], null))).get(3, TimeUnit.SECONDS);
                        return null;
                    }
                    default -> throw new AssertionError(method.getName());
                }
            });
        }
        public void close() throws Exception { io.shutdownNow(); assertTrue(io.awaitTermination(3, TimeUnit.SECONDS)); }
    }
    @Test void twoActorsReconnectIndependentlyKeepIdentityAndCloseAllConnections() throws Exception {
        var plan = multiple();
        try (var connections = new Connections(plan, false)) {
            var player = new ActionPlayer(RUN, 25565, "calibrate", plan, connections);
            var report = player.run(5);
            assertEquals(true, report.get("passed"), report.toString());
            assertEquals(2, connections.visits.get(ActionPlayer.username(RUN, 0)));
            assertEquals(2, connections.visits.get(ActionPlayer.username(RUN, 1)));
            assertEquals(4, connections.connected.size());
            assertTrue(connections.connected.stream().noneMatch(AtomicBoolean::get));
            @SuppressWarnings("unchecked") var actors = (List<Map<String, Object>>) report.get("actors");
            assertNotEquals(actors.get(0).get("uuid"), actors.get(1).get("uuid"));
            assertEquals(2, ((List<?>) actors.get(0).get("sessions")).size());
            assertEquals(2, ((List<?>) actors.get(1).get("sessions")).size());
        }
    }
    @Test void timeoutClosesEveryOwnedConnectionAndCannotReportPartialSuccess() throws Exception {
        var plan = multiple();
        try (var connections = new Connections(plan, true)) {
            var report = new ActionPlayer(RUN, 25565, "idle", plan, connections).run(1);
            assertEquals(false, report.get("passed"));
            assertEquals("Timed out waiting for actions", report.get("error"));
            assertEquals(2, connections.connected.size());
            assertTrue(connections.connected.stream().noneMatch(AtomicBoolean::get));
        }
    }
    @Test void connectionCreationFailureClosesAlreadyRegisteredPeer() throws Exception {
        var plan = multiple();
        try (var connections = new Connections(plan, true)) {
            ActionPlayer.ConnectionFactory factory = (name, callbacks) -> {
                if (name.endsWith("_1")) throw new IllegalStateException("Deliberate connect failure");
                return connections.create(name, callbacks);
            };
            var report = new ActionPlayer(RUN, 25565, "calibrate", plan, factory).run(3);
            assertEquals(false, report.get("passed"));
            assertTrue(report.get("error").toString().contains("Deliberate connect failure"));
            assertTrue(connections.connected.stream().noneMatch(AtomicBoolean::get));
        }
    }
}
