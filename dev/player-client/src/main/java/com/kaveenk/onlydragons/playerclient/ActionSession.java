package com.kaveenk.onlydragons.playerclient;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;

/** State for precisely one connection. A reconnect creates a new instance. */
final class ActionSession extends SessionAdapter {
    interface Owner { void ended(ActionSession session); void failed(String error); }
    private record Effects(List<Packet> packets, ActionPlan.Step step, String disconnectReason, Map<String, Object> evidence) {
        Effects(List<Packet> packets, ActionPlan.Step step, String disconnectReason) { this(packets, step, disconnectReason, Map.of()); }
    }
    private final String runId, actorId, behavior;
    private final ActionPlan plan;
    private final boolean observeUi;
    final ActionPlan.SessionPlan definition;
    private final Owner owner;
    private final long started = System.currentTimeMillis();
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final Map<String, UUID> targets = new LinkedHashMap<>();
    private final List<Map<String, Object>> bindings = new ArrayList<>();
    private final Map<UUID, Integer> entities = new HashMap<>();
    private final Map<Integer, UUID> entityIds = new HashMap<>();
    private final BossBarObservation bossBars = new BossBarObservation();
    private final List<Map<String,Object>> styledMessages = new ArrayList<>();
    private final InventoryState inventory = new InventoryState();
    private boolean login, loaded, requestedDisconnect, disconnected, dead;
    private int next, sequence, teleports;
    private String error = "";
    private long completed;
    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround;

    ActionSession(String runId, String actorId, String behavior, ActionPlan plan,
                  ActionPlan.SessionPlan definition, Owner owner) {
        this.runId = runId; this.actorId = actorId; this.behavior = behavior;
        this.observeUi = plan.planId().startsWith("dragon-presentation-") || plan.planId().startsWith("dragon-restart-");
        this.plan = plan; this.definition = definition; this.owner = owner;
    }

    @Override public void packetReceived(Session session, Packet packet) {
        try {
            Effects effect = receive(packet);
            // No network operation under state monitor: disconnect may await an I/O
            // callback that needs this same state. Game packet callbacks are ordered.
            for (Packet outgoing : effect.packets) session.send(outgoing);
            if (effect.step != null) {
                synchronized (this) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("id", effect.step.id()); row.put("action", effect.step.action()); row.put("args", effect.step.args());
                    row.put("submittedAtEpochMs", System.currentTimeMillis());
                    row.put("packetTypes", effect.packets.stream().map(p -> p.getClass().getSimpleName()).toList());
                    row.putAll(effect.evidence); steps.add(Collections.unmodifiableMap(row));
                }
            }
            if (effect.disconnectReason != null) session.disconnect(Component.text(effect.disconnectReason));
        } catch (RuntimeException failure) {
            failed(failure.toString());
            owner.failed(failure.toString());
            session.disconnect(Component.text("Action fixture failure"));
        }
    }

    private synchronized Effects receive(Packet packet) {
        if (!error.isEmpty() || disconnected || requestedDisconnect) return new Effects(List.of(), null, null);
        inventory.receive(packet);
        if (observeUi && packet instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket boss) bossBars.receive(boss);
        var outgoing = new ArrayList<Packet>();
        if (packet instanceof ClientboundLoginPacket joined) {
            check(!joined.isOnlineMode(), "Action fixture requires disposable offline profile");
            check(!login, "Duplicate login packet"); login = true;
            if (behavior.equals("early-exit")) {
                error = "Deliberate early client exit";
                return new Effects(List.of(), null, error);
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            var relative = position.getRelatives();
            x = position.getPosition().getX() + (relative.contains(PositionElement.X) ? x : 0);
            y = position.getPosition().getY() + (relative.contains(PositionElement.Y) ? y : 0);
            z = position.getPosition().getZ() + (relative.contains(PositionElement.Z) ? z : 0);
            yaw = position.getYRot() + (relative.contains(PositionElement.Y_ROT) ? yaw : 0);
            pitch = position.getXRot() + (relative.contains(PositionElement.X_ROT) ? pitch : 0);
            check(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(yaw) && Float.isFinite(pitch), "Nonfinite server position");
            outgoing.add(new ServerboundAcceptTeleportationPacket(position.getId())); teleports++;
            if (!loaded) { loaded = true; outgoing.add(ServerboundPlayerLoadedPacket.INSTANCE); }
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            outgoing.add(new ServerboundChunkBatchReceivedPacket(10.0f));
        } else if (packet instanceof ClientboundSetHealthPacket health) {
            dead = health.getHealth() <= 0;
        } else if (packet instanceof ClientboundRespawnPacket) {
            // A new world/session view invalidates all previously observed network IDs.
            entities.clear(); entityIds.clear(); loaded = false; dead = false;
            inventory.reset();
        } else if (packet instanceof ClientboundAddEntityPacket entity) {
            check(entities.size() < 4096 || entities.containsKey(entity.getUuid()), "Observed entity limit exceeded");
            UUID old = entityIds.put(entity.getEntityId(), entity.getUuid());
            if (old != null) entities.remove(old);
            Integer previous = entities.put(entity.getUuid(), entity.getEntityId());
            if (previous != null && previous != entity.getEntityId()) entityIds.remove(previous);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket removal) {
            for (int id : removal.getEntityIds()) {
                UUID uuid = entityIds.remove(id); if (uuid != null) entities.remove(uuid);
            }
        } else if (packet instanceof ClientboundSystemChatPacket chat && !chat.isOverlay()) {
            String value = flatten(chat.getContent());
            if (observeUi && value.startsWith("OD_UI_CHECK:" + runId + ":")) {
                bossBars.sample(value.substring(("OD_UI_CHECK:" + runId + ":").length()));
                return new Effects(List.of(), null, null);
            }
            if (value.startsWith("OD_BIND:")) { bind(value); return new Effects(List.of(), null, null); }
            if (value.startsWith("OD_ACTION:")) return trigger(value);
            if (value.startsWith("OD_PLAYER:")) return new Effects(List.of(), null, null);
            if (!value.isEmpty()) { check(messages.size() < 128, "Player message count exceeded capture bound"); messages.add(value);
                if (observeUi) {
                String json=net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(chat.getContent());
                check(json.length()<=16384,"Styled message bound exceeded");
                styledMessages.add(Map.of("text",value,"json",json));
                } }
        }
        return new Effects(List.copyOf(outgoing), null, null);
    }

    private void bind(String value) {
        String[] pieces = value.split(":", -1);
        if (pieces.length >= 2 && !pieces[1].equals(runId)) return;
        check(pieces.length == 5 && pieces[2].equals(plan.sha256()), "Malformed/stale target binding");
        check(plan.targets().contains(pieces[3]), "Undeclared target binding");
        check(pieces[4].matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"), "Invalid target UUID");
        UUID uuid = UUID.fromString(pieces[4]);
        check(!targets.containsKey(pieces[3]), "Duplicate/changed target binding");
        targets.put(pieces[3], uuid);
        bindings.add(Map.of("targetRef", pieces[3], "uuid", uuid.toString(), "receivedAtEpochMs", System.currentTimeMillis()));
    }

    private Effects trigger(String value) {
        String[] pieces = value.split(":", -1);
        if (pieces.length >= 2 && !pieces[1].equals(runId)) return new Effects(List.of(), null, null);
        if (pieces.length == 6 && !pieces[3].equals(actorId)) return new Effects(List.of(), null, null);
        check(pieces.length == 6 && pieces[2].equals(plan.sha256()) && pieces[4].equals(definition.id()), "Malformed/stale action marker");
        check(login && loaded, "Action before player loaded");
        check(next < definition.steps().size() && definition.steps().get(next).id().equals(pieces[5]), "Unexpected action step");
        if (behavior.equals("idle")) return new Effects(List.of(), null, null);
        ActionPlan.Step step = definition.steps().get(next);
        InventoryState.Click click = step.action().equals("inventoryClick") ? inventory.click(step) : null;
        List<Packet> packets = click == null ? packets(step) : List.of(click.packet());
        next++;
        String disconnect = null;
        if (step.action().equals("disconnect") || step.action().equals("reconnect")) {
            inventory.requireSettled();
            requestedDisconnect = true; disconnect = "Declared action " + step.action();
        }
        Map<String, Object> evidence = click != null ? click.evidence() : step.action().equals("attackEntity")
                ? Map.of("targetUuid", targets.get(step.text("targetRef")).toString(),
                         "networkEntityId", ((ServerboundAttackPacket) packets.getFirst()).getEntityId()) : Map.of();
        return new Effects(packets, step, disconnect, evidence);
    }

    private List<Packet> packets(ActionPlan.Step step) {
        return switch (step.action()) {
            case "selectSlot" -> List.of(new ServerboundSetCarriedItemPacket(step.integer("slot")));
            case "look" -> {
                yaw = (float) step.number("yaw"); pitch = (float) step.number("pitch");
                yield List.of(new ServerboundMovePlayerRotPacket(onGround, false, yaw, pitch));
            }
            case "move" -> {
                double nx = step.number("x"), ny = step.number("y"), nz = step.number("z");
                check(Math.abs(nx - x) <= 8 && Math.abs(ny - y) <= 8 && Math.abs(nz - z) <= 8, "Movement exceeds per-action displacement bound");
                x = nx; y = ny; z = nz; onGround = step.bool("onGround");
                yield List.of(new ServerboundMovePlayerPosPacket(onGround, false, x, y, z));
            }
            case "command" -> List.of(new ServerboundChatCommandPacket(step.text("command")));
            case "useItem" -> List.of(new ServerboundUseItemPacket(hand(step), ++sequence, yaw, pitch));
            case "releaseUse" -> List.of(playerAction(PlayerAction.RELEASE_USE_ITEM));
            case "swapHands" -> List.of(playerAction(PlayerAction.SWAP_HANDS));
            case "dropItem" -> List.of(playerAction(step.bool("all") ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM));
            case "swing" -> List.of(new ServerboundSwingPacket(hand(step)));
            case "attackEntity" -> {
                UUID target = targets.get(step.text("targetRef"));
                check(target != null && entities.containsKey(target), "Attack target was not bound and observed, or was removed");
                yield List.of(new ServerboundAttackPacket(entities.get(target)));
            }
            case "interactBlock" -> List.of(new ServerboundUseItemOnPacket(
                    Vector3i.from(step.integer("x"), step.integer("y"), step.integer("z")),
                    Direction.valueOf(step.text("face").toUpperCase(Locale.ROOT)), hand(step),
                    (float) step.number("cursorX"), (float) step.number("cursorY"), (float) step.number("cursorZ"),
                    step.bool("insideBlock"), false, ++sequence));
            case "respawn" -> {
                check(dead, "Respawn requested before observed death");
                yield List.of(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
            }
            case "reconnect", "disconnect" -> List.of();
            default -> throw new IllegalStateException("Unvalidated action");
        };
    }
    private Packet playerAction(PlayerAction action) { return new ServerboundPlayerActionPacket(action, Vector3i.ZERO, Direction.DOWN, ++sequence); }
    private static Hand hand(ActionPlan.Step step) { return step.text("hand").equals("main") ? Hand.MAIN_HAND : Hand.OFF_HAND; }
    static String flatten(Component component) {
        var text = new StringBuilder();
        ComponentFlattener.basic().flatten(component, value -> { check(text.length() + value.length() <= 2048, "Player message exceeded capture bound"); text.append(value); });
        return text.toString();
    }
    static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    @Override public void disconnected(DisconnectedEvent event) {
        synchronized (this) {
            if (disconnected) return;
            if (!requestedDisconnect && error.isEmpty()) error = "Unexpected disconnect: " + event.getReason();
            if (event.getCause() != null && error.isEmpty()) error = event.getCause().toString();
            disconnected = true; completed = System.currentTimeMillis();
        }
        owner.ended(this);
    }
    synchronized void failed(String reason) { if (error.isEmpty()) error = reason; }
    synchronized boolean successful() { return error.isEmpty() && login && loaded && teleports > 0 && requestedDisconnect && disconnected && steps.size() == definition.steps().size(); }
    synchronized boolean wantsReconnect() { return successful() && definition.steps().getLast().action().equals("reconnect"); }
    synchronized int reconnectDelay() { return definition.steps().getLast().integer("delayMillis"); }
    synchronized Map<String, Object> report() {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", definition.id()); result.put("startedAtEpochMs", started); result.put("completedAtEpochMs", completed);
        result.put("loginReceived", login); result.put("playerLoadedSent", loaded); result.put("teleportsAcknowledged", teleports);
        result.put("steps", List.copyOf(steps)); result.put("messages", List.copyOf(messages)); result.put("bindings", List.copyOf(bindings));
        if (bossBars.sampled()) { result.put("bossBars",bossBars.report()); result.put("styledMessages",List.copyOf(styledMessages)); }
        result.put("inventorySnapshots", inventory.snapshots());
        result.put("inventoryConfirmations", inventory.confirmations());
        result.put("disconnected", disconnected); result.put("passed", successful()); result.put("error", error);
        return Collections.unmodifiableMap(result);
    }
}
