package com.kaveenk.onlydragons.playerclient;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

/** Disposable loopback protocol actor; never authenticates or follows server transfers. */
public final class ProtocolPlayer extends SessionAdapter {
    private static final Properties PINS = readPins();
    public static final String ARTIFACT = PINS.getProperty("artifact");
    static final String EXPECTED_MINECRAFT = PINS.getProperty("minecraftVersion");
    static final int EXPECTED_PROTOCOL = Integer.parseInt(PINS.getProperty("protocolVersion"));
    private final String runId;
    private final String behavior;
    private final List<String> actions = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    static final int MAX_MESSAGES = 128;
    static final int MAX_MESSAGE_LENGTH = 2048;
    private final CountDownLatch disconnected = new CountDownLatch(1);
    private boolean login;
    private boolean loaded;
    private boolean requestedQuit;
    private int teleports;
    private String error;

    ProtocolPlayer(String runId, String behavior) { this.runId = runId; this.behavior = behavior; }

    private static Properties readPins() {
        var pins = new Properties();
        try (var stream = ProtocolPlayer.class.getResourceAsStream("/player-client.properties")) {
            if (stream == null) throw new IllegalStateException("Missing built client pins");
            pins.load(stream);
            return pins;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    static String username(String runId) {
        if (runId == null || !runId.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid run ID");
        return "od_" + runId.substring(0, 13);
    }

    private record Outbound(List<Packet> packets, String disconnectReason) {}

    @Override public void packetReceived(Session session, Packet packet) {
        Outbound outbound;
        try {
            outbound = receive(packet);
        } catch (IllegalStateException invalid) {
            synchronized (this) { error = invalid.getMessage(); }
            session.disconnect(Component.text(invalid.getMessage()));
            return;
        }
        // MCProtocolLib's game executor preserves action order. Its I/O callbacks
        // may run concurrently: never hold our state monitor while sending or
        // waiting for disconnect, which waits for that I/O event loop to close.
        for (Packet outgoing : outbound.packets()) session.send(outgoing);
        if (outbound.disconnectReason() != null) session.disconnect(Component.text(outbound.disconnectReason()));
    }

    private synchronized Outbound receive(Packet packet) {
        if (error != null || requestedQuit || disconnected.getCount() == 0) return new Outbound(List.of(), null);
        var outgoing = new ArrayList<Packet>();
        String disconnectReason = null;
        if (packet instanceof ClientboundLoginPacket joined) {
            if (joined.isOnlineMode()) throw new IllegalStateException("Fixture requires its disposable offline profile");
            login = true;
            if (behavior.equals("early-exit")) {
                error = "Deliberate early client exit";
                disconnectReason = error;
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            outgoing.add(new ServerboundAcceptTeleportationPacket(position.getId()));
            teleports++;
            if (!loaded) { loaded = true; outgoing.add(ServerboundPlayerLoadedPacket.INSTANCE); }
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            outgoing.add(new ServerboundChunkBatchReceivedPacket(10.0f));
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            String action = captureChat(chat.getContent(), chat.isOverlay());
            if (action != null && !behavior.equals("idle")) disconnectReason = act(action, outgoing);
        }
        return new Outbound(List.copyOf(outgoing), disconnectReason);
    }

    // Record received ordinary text, never control markers or action-bar overlays.
    // Bounded flattening fails closed instead of truncating possible evidence.
    synchronized String captureChat(Component component, boolean overlay) {
        if (overlay) return null;
        var text = new StringBuilder();
        ComponentFlattener.basic().flatten(component, part -> {
            if (text.length() + part.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalStateException("Player message exceeded capture bound");
            }
            text.append(part);
        });
        String value = text.toString();
        String prefix = "OD_PLAYER:" + runId + ":";
        if (value.startsWith("OD_PLAYER:")) {
            return value.startsWith(prefix) ? value.substring(prefix.length()) : null;
        }
        if (value.isEmpty()) return null;
        if (messages.size() >= MAX_MESSAGES) throw new IllegalStateException("Player message count exceeded capture bound");
        messages.add(value);
        return null;
    }

    synchronized List<String> capturedMessages() { return List.copyOf(messages); }

    private String act(String action, List<Packet> outgoing) {
        List<String> expected = List.of("select", "draw", "release", "quit");
        if (actions.size() >= expected.size() || !expected.get(actions.size()).equals(action)) {
            throw new IllegalStateException("Unexpected player action: " + action);
        }
        if (!login || !loaded) throw new IllegalStateException("Action before player loaded");
        actions.add(action);
        switch (action) {
            case "select" -> outgoing.add(new ServerboundSetCarriedItemPacket(1));
            case "draw" -> outgoing.add(new ServerboundUseItemPacket(Hand.MAIN_HAND, 1, 0, 0));
            case "release" -> outgoing.add(new ServerboundPlayerActionPacket(
                    PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, 2));
            case "quit" -> { requestedQuit = true; return "Calibration complete"; }
            default -> throw new IllegalStateException(action);
        }
        return null;
    }

    @Override public synchronized void disconnected(DisconnectedEvent event) {
        if (!requestedQuit && error == null) error = "Unexpected disconnect: " + event.getReason();
        if (event.getCause() != null) error = event.getCause().toString();
        disconnected.countDown();
    }

    synchronized Map<String, Object> report(long started) {
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("runId", runId);
        report.put("username", username(runId));
        report.put("authentication", "offline-disposable-loopback");
        report.put("artifact", ARTIFACT);
        report.put("minecraftVersion", MinecraftCodec.CODEC.getMinecraftVersion());
        report.put("protocolVersion", MinecraftCodec.CODEC.getProtocolVersion());
        report.put("startedAtEpochMs", started);
        report.put("completedAtEpochMs", System.currentTimeMillis());
        report.put("loginReceived", login);
        report.put("playerLoadedSent", loaded);
        report.put("teleportsAcknowledged", teleports);
        report.put("actions", List.copyOf(actions));
        report.put("messages", capturedMessages());
        report.put("disconnected", disconnected.getCount() == 0);
        report.put("passed", error == null && login && loaded && teleports > 0 && requestedQuit
                && disconnected.getCount() == 0 && actions.equals(List.of("select", "draw", "release", "quit")));
        report.put("error", error == null ? "" : error);
        return report;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Expected runId port reportPath timeoutSeconds behavior");
        String name = username(args[0]);
        int port = Integer.parseInt(args[1]);
        int timeout = Integer.parseInt(args[3]);
        if (port < 1 || port > 65535 || timeout < 1 || timeout > 300
                || !List.of("calibrate", "early-exit", "idle").contains(args[4])) {
            throw new IllegalArgumentException("Invalid bounded fixture arguments");
        }
        if (MinecraftCodec.CODEC.getProtocolVersion() != EXPECTED_PROTOCOL
                || !MinecraftCodec.CODEC.getMinecraftVersion().equals(EXPECTED_MINECRAFT)) {
            throw new IllegalStateException("Wrong MCProtocolLib codec");
        }
        long started = System.currentTimeMillis();
        var actor = new ProtocolPlayer(args[0], args[4]);
        var callbacks = Executors.newSingleThreadExecutor();
        ClientSession session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress("127.0.0.1", port))
                .setProtocol(new MinecraftProtocol(name)).setPacketHandlerExecutor(callbacks).create();
        session.setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);
        session.addListener(actor);
        Thread.ofPlatform().daemon().name("fixture-stop-input").start(() -> {
            try {
                if ("stop".equals(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine())) {
                    session.disconnect(Component.text("Runner cleanup"));
                }
            } catch (Exception ignored) { /* Runner also owns a bounded process-group fallback. */ }
        });
        try {
            session.connect(false);
            if (!actor.disconnected.await(timeout, TimeUnit.SECONDS)) {
                synchronized (actor) { actor.error = "Timed out waiting for calibration"; }
            }
        } catch (Exception failure) {
            synchronized (actor) { actor.error = failure.toString(); }
        } finally {
            if (session.isConnected()) session.disconnect(Component.text("Fixture cleanup"));
            callbacks.shutdown();
            if (!callbacks.awaitTermination(3, TimeUnit.SECONDS)) {
                callbacks.shutdownNow();
                synchronized (actor) { actor.error = "Client callback executor did not stop"; }
            }
        }
        Map<String, Object> report = actor.report(started);
        Path destination = Path.of(args[2]);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, new Gson().toJson(report), StandardCharsets.UTF_8);
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("OD_PLAYER_CLIENT_COMPLETE passed=" + report.get("passed"));
        if (!Boolean.TRUE.equals(report.get("passed"))) System.exit(1);
    }
}
