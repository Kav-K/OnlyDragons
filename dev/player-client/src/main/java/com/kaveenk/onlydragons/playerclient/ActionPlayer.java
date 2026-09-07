package com.kaveenk.onlydragons.playerclient;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;

/**
 * Runs all actors in one isolated loopback process and produces schema-2 receipts.
 * Connection creation, reconnect scheduling and cohort cleanup are serialized on the
 * owned lifecycle executor. Each actor has a separate ordered packet-callback executor.
 * A failed actor fails the cohort; receipts prove protocol progress, while companion
 * Paper assertions must establish command, movement, inventory and damage acceptance.
 */
public final class ActionPlayer {
    /**
     * Connection seam for the fixed loopback transport and pure lifecycle tests.
     * Implementations attach packet handling to the supplied executor; the owner installs
     * listeners and disables transfer following before connecting.
     */
    @FunctionalInterface interface ConnectionFactory {
        /** Creates an unconnected transport for the stable actor name and its ordered callback executor. */
        ClientSession create(String name, ExecutorService callbacks);
    }
    private final String runId, behavior;
    private final int port;
    private final ActionPlan plan;
    private final ConnectionFactory connectionFactory;
    private final ScheduledExecutorService lifecycle = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch complete = new CountDownLatch(1);
    private final List<ActorRun> actors = new ArrayList<>();
    private volatile String error = "";
    private boolean closing;

    /**
     * Lifecycle-executor-owned identity and connection history.
     * Callbacks enqueue lifecycle work rather than connecting or retiring peers directly.
     */
    private final class ActorRun implements ActionSession.Owner {
        final ActionPlan.Actor definition;
        final String username;
        final ExecutorService callbacks = Executors.newSingleThreadExecutor();
        final List<ActionSession> history = new ArrayList<>();
        ClientSession connection;
        int sessionIndex;
        boolean done;

        /** Creates a stable offline username and an owned callback executor for this actor index. */
        ActorRun(ActionPlan.Actor definition, int index) { this.definition = definition; this.username = username(runId, index); }
        /**
         * Registers a fresh session before opening its transport so partial creation failures
         * can close every already-owned peer. Called only through the lifecycle executor.
         */
        void connect() {
            if (closing) return;
            var session = new ActionSession(runId, definition.id(), behavior, plan, definition.sessions().get(sessionIndex), this);
            history.add(session);
            try {
                connection = connectionFactory.create(username, callbacks);
                connection.setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);
                connection.addListener(session);
                connection.connect(false);
            }
            catch (RuntimeException failure) { session.failed(failure.toString()); closeAll(failure.toString()); }
        }
        /**
         * Hands the actual disconnect callback to the lifecycle executor. Rejects stale sessions;
         * only a successful declared reconnect may schedule another connection.
         */
        @Override public void ended(ActionSession session) {
            enqueue(() -> {
                if (closing) return;
                if (history.getLast() != session) { closeAll("Stale session callback"); return; }
                if (!session.successful()) { closeAll("Actor " + definition.id() + "/" + session.definition.id() + " failed"); return; }
                if (session.wantsReconnect()) {
                    sessionIndex++;
                    lifecycle.schedule(this::connect, session.reconnectDelay(), TimeUnit.MILLISECONDS);
                } else {
                    done = true;
                    if (actors.stream().allMatch(actor -> actor.done)) complete.countDown();
                }
            });
        }
        /** Schedules cohort failure without performing network cleanup on the packet callback thread. */
        @Override public void failed(String reason) { enqueue(() -> closeAll(reason)); }
        /** Copies this actor identity and connection history after the owned callbacks have been drained. */
        Map<String, Object> report() {
            return Map.of("id", definition.id(), "username", username,
                    "uuid", offlineUuid(username).toString(), "sessions", history.stream().map(ActionSession::report).toList());
        }
    }

    /** Creates the production fixture transport, fixed to IPv4 loopback and an offline protocol identity. */
    ActionPlayer(String runId, int port, String behavior, ActionPlan plan) {
        this(runId, port, behavior, plan, (name, callbacks) -> ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress("127.0.0.1", port))
                .setProtocol(new MinecraftProtocol(name)).setPacketHandlerExecutor(callbacks).create());
    }
    /**
     * Constructs an unstarted cohort from an already validated plan.
     * The injectable factory is for transport/lifecycle tests, not an action-plan host option.
     */
    ActionPlayer(String runId, int port, String behavior, ActionPlan plan, ConnectionFactory connectionFactory) {
        username(runId, 0);
        ActionPlan.require(port > 0 && port <= 65535 && List.of("calibrate", "early-exit", "idle").contains(behavior), "Invalid client arguments");
        this.runId = runId; this.port = port; this.behavior = behavior; this.plan = plan; this.connectionFactory = connectionFactory;
        for (int i = 0; i < plan.actors().size(); i++) actors.add(new ActorRun(plan.actors().get(i), i));
    }
    /** Derives the exact 15-character actor name from the validated run ID and index 0-3. */
    static String username(String runId, int index) {
        ActionPlan.require(runId != null && runId.matches("[a-f0-9]{32}") && index >= 0 && index < 4, "Invalid actor identity");
        return "od_" + runId.substring(0, 10) + "_" + index;
    }
    /** Derives the server-compatible offline UUID from the stable UTF-8 username. */
    static UUID offlineUuid(String username) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)); }
    /** Serializes lifecycle work; late callbacks after executor shutdown cannot reopen connections. */
    private void enqueue(Runnable work) {
        try { lifecycle.execute(work); } catch (RejectedExecutionException ignored) { /* The owned lifecycle is already closed. */ }
    }
    /**
     * Stops future connects and retires every owned live transport, retaining the first error.
     * Must run on the lifecycle executor. Unfinished sessions receive the supplied reason;
     * completed actors retain their individual receipt even when the cohort fails.
     */
    private void closeAll(String reason) {
        if (!reason.isEmpty() && error.isEmpty()) error = reason;
        closing = true;
        // All connects/reconnects and this close run on the same executor; a stop
        // cannot race past registration and leave a newly opened connection.
        for (ActorRun actor : actors) {
            if (!actor.done && !reason.isEmpty() && !actor.history.isEmpty()) actor.history.getLast().failed(reason);
            try { if (actor.connection != null && actor.connection.isConnected()) actor.connection.disconnect(Component.text("Runner cleanup")); }
            catch (RuntimeException failure) { if (error.isEmpty()) error = failure.toString(); }
        }
        complete.countDown();
    }
    /**
     * Listens once on a daemon thread for the runner's exact {@code stop} line.
     * The queued failure preserves a distinct cleanup-abort receipt; this is not a successful terminal action.
     */
    private void listenForStopInput() {
        Thread.ofPlatform().daemon().name("actions-stop-input").start(() -> {
            try {
                if ("stop".equals(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine()))
                    enqueue(() -> closeAll("Runner cleanup before action completion"));
            } catch (Exception ignored) { /* Runner retains process-group fallback. */ }
        });
    }
    /**
     * Runs the cohort to completion or timeout, then closes transports and drains executors.
     * A passing result requires every declared connection and step to finish successfully;
     * it does not infer server-side acceptance from packet submission.
     * @param timeout maximum seconds to await actions (the entry point admits 1-300)
     * @return detached report after bounded cleanup, including any cleanup failure
     * @throws Exception if waiting or lifecycle coordination cannot complete normally
     */
    Map<String, Object> run(int timeout) throws Exception {
        long started = System.currentTimeMillis();
        enqueue(() -> actors.forEach(ActorRun::connect));
        try {
            if (!complete.await(timeout, TimeUnit.SECONDS)) error = "Timed out waiting for actions";
        } finally {
            try { lifecycle.submit(() -> closeAll(error)).get(5, TimeUnit.SECONDS); }
            catch (Exception failure) { if (error.isEmpty()) error = "Client lifecycle cleanup failed: " + failure; }
            lifecycle.shutdownNow();
            if (!lifecycle.awaitTermination(3, TimeUnit.SECONDS) && error.isEmpty()) error = "Client lifecycle executor did not stop";
            for (ActorRun actor : actors) actor.callbacks.shutdown();
            for (ActorRun actor : actors) {
                if (!actor.callbacks.awaitTermination(3, TimeUnit.SECONDS)) {
                    actor.callbacks.shutdownNow();
                    if (error.isEmpty()) error = "Client callback executor did not stop";
                }
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 2); result.put("runId", runId); result.put("planId", plan.planId()); result.put("planSha256", plan.sha256());
        result.put("authentication", "offline-disposable-loopback"); result.put("artifact", ProtocolPlayer.ARTIFACT);
        result.put("minecraftVersion", MinecraftCodec.CODEC.getMinecraftVersion()); result.put("protocolVersion", MinecraftCodec.CODEC.getProtocolVersion());
        result.put("startedAtEpochMs", started); result.put("completedAtEpochMs", System.currentTimeMillis());
        result.put("actors", actors.stream().map(ActorRun::report).toList());
        result.put("passed", error.isEmpty() && actors.stream().allMatch(actor -> actor.done
                && actor.history.size() == actor.definition.sessions().size() && actor.history.stream().allMatch(ActionSession::successful)));
        result.put("error", error); return result;
    }
    /**
     * Runner-only entry point: validates the codec and plan before connecting to loopback.
     * Writes UTF-8 JSON through an atomic sibling-file move after cleanup; a failed receipt
     * exits with status 1. No credentials or remote hostname are accepted.
     * @param args run ID, port, report path, timeout seconds, behavior, plan path and plan SHA-256
     * @throws Exception if arguments, plan loading, execution or report publication fail
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) throw new IllegalArgumentException("Expected runId port reportPath timeoutSeconds behavior planPath planSha256");
        int timeout = Integer.parseInt(args[3]);
        ActionPlan.require(timeout > 0 && timeout <= 300, "Invalid timeout");
        ActionPlan.require(MinecraftCodec.CODEC.getProtocolVersion() == ProtocolPlayer.EXPECTED_PROTOCOL
                && MinecraftCodec.CODEC.getMinecraftVersion().equals(ProtocolPlayer.EXPECTED_MINECRAFT), "Wrong pinned protocol codec");
        var plan = ActionPlan.load(Path.of(args[5]), args[6]);
        var actor = new ActionPlayer(args[0], Integer.parseInt(args[1]), args[4], plan);
        actor.listenForStopInput();
        var report = actor.run(timeout);
        Path destination = Path.of(args[2]);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, new Gson().toJson(report), StandardCharsets.UTF_8);
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("OD_PLAYER_ACTIONS_COMPLETE passed=" + report.get("passed"));
        if (!Boolean.TRUE.equals(report.get("passed"))) System.exit(1);
    }
}
