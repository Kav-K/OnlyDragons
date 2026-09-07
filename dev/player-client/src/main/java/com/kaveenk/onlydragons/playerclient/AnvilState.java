package com.kaveenk.onlydragons.playerclient;

import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.*;
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.*;

/**
 * Observes a real 39-slot ANVIL menu independently of player-window authority.
 * Owned and serialized by {@link ActionSession}; no optimistic inventory edits or XP
 * debits occur here. Actions require an observed ANVIL open and fresh full contents.
 * Received costs/XP and packet hashes supplement the companion's actual event and
 * conservation assertions; they do not themselves establish transaction acceptance.
 */
final class AnvilState {
    /**
     * An anvil packet request and the exact open/snapshot used to prepare it.
     * @param packet click, rename or close request for the currently observed menu
     * @param evidence immutable container, state ID and snapshot provenance
     */
    record Action(Packet packet, Map<String, Object> evidence) {}
    private final List<Map<String,Object>> opens = new ArrayList<>(), snapshots = new ArrayList<>(), costs = new ArrayList<>(), xp = new ArrayList<>(), closes = new ArrayList<>();
    private int container = -1, openSequence, stateId;
    private boolean ready, pending;
    private String cursor;
    private String[] slots;
    private Map<String,Object> lastSnapshot;

    /**
     * Tracks matching menu opens, full contents, cost/XP packets and closes with bounded histories.
     * Any matching slot delta, changed cursor or stateless player-inventory update removes
     * readiness; only full contents settle a pending click/rename. Other menu types cannot authorize actions.
     */
    void receive(Packet packet) {
        if (packet instanceof ClientboundOpenScreenPacket open) {
            ready = false; pending = false; container = -1;
            if (open.getType() != ContainerType.ANVIL) return;
            check(open.getContainerId() > 0, "Invalid anvil container ID");
            container = open.getContainerId(); openSequence = opens.size() + 1;
            add(opens, row(open, Map.of("openSequence",openSequence,"containerId",container,"menuType","ANVIL")));
        } else if (packet instanceof ClientboundContainerSetContentPacket full && container > 0 && full.getContainerId() == container) {
            check(full.getItems().length == 39, "Anvil requires exactly 39 slots");
            check(full.getStateId() >= 0 && full.getStateId() <= 32767, "Invalid anvil state ID");
            stateId = full.getStateId();
            slots = Arrays.stream(full.getItems()).map(AnvilState::itemDigest).toArray(String[]::new);
            cursor = itemDigest(full.getCarriedItem());
            var data = new LinkedHashMap<String,Object>();
            data.put("openSequence",openSequence); data.put("containerId",container); data.put("stateId",stateId);
            data.put("slotCount",39); data.put("slotSha256",List.of(slots)); data.put("cursorSha256",cursor);
            data.put("amounts",Arrays.stream(full.getItems()).map(i -> i == null ? 0 : i.getAmount()).toList());
            data.put("cursorAmount",full.getCarriedItem() == null ? 0 : full.getCarriedItem().getAmount());
            lastSnapshot = row(full,data); add(snapshots,lastSnapshot); ready = true; pending = false;
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot && container > 0 && slot.getContainerId() == container) {
            // Require a new full snapshot even for unchanged deltas; no guessed state advancement.
            ready = false;
        } else if (packet instanceof ClientboundSetCursorItemPacket item) {
            if (!Objects.equals(cursor,itemDigest(item.getContents()))) ready = false;
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket) {
            ready = false;
        } else if (packet instanceof ClientboundContainerSetDataPacket cost && container > 0 && cost.getContainerId() == container) {
            check(cost.getRawProperty() == 0, "Unexpected anvil property");
            add(costs,row(cost,Map.of("openSequence",openSequence,"containerId",container,"cost",cost.getValue())));
        } else if (packet instanceof ClientboundSetExperiencePacket experience) {
            check(Float.isFinite(experience.getExperience()) && experience.getExperience() >= 0 && experience.getExperience() <= 1,
                    "Invalid received XP fraction");
            add(xp,row(experience,Map.of("level",experience.getLevel(),"fraction",experience.getExperience(),"total",experience.getTotalExperience())));
        } else if (packet instanceof ClientboundContainerClosePacket close && container > 0 && close.getContainerId() == container) {
            add(closes,row(close,Map.of("openSequence",openSequence,"containerId",container,"source","server")));
            container = -1; ready = false; pending = false;
        }
    }

    /**
     * Prepares an admitted click, rename or close against the current full menu snapshot.
     * Clicks and renames wait for authoritative resynchronization; a prepared client close
     * records its own source explicitly. The caller sends the packet outside its state lock.
     */
    Action action(ActionPlan.Step step) {
        check(container > 0 && ready && !pending && lastSnapshot != null, "Anvil action needs an observed open and fresh full snapshot");
        var evidence = Map.<String,Object>of("anvilOpenSequence",openSequence,"containerId",container,"stateId",stateId,
                "anvilSnapshotSequence",snapshots.size(),"anvilSnapshotSha256",lastSnapshot.get("sha256"));
        Packet packet;
        switch (step.action()) {
            case "anvilClick" -> {
                boolean shift = step.text("button").startsWith("shift-");
                boolean right = step.text("button").endsWith("right");
                packet = new ServerboundContainerClickPacket(container,stateId,step.integer("slot"),
                        step.text("button").equals("drop") ? ContainerActionType.DROP_ITEM : step.text("button").equals("hotbar-1") ? ContainerActionType.MOVE_TO_HOTBAR_SLOT : shift ? ContainerActionType.SHIFT_CLICK_ITEM : ContainerActionType.CLICK_ITEM,
                        step.text("button").equals("drop") ? DropItemAction.DROP_FROM_SELECTED : step.text("button").equals("hotbar-1") ? MoveToHotbarAction.SLOT_1 : shift ? (right ? ShiftClickItemAction.RIGHT_CLICK : ShiftClickItemAction.LEFT_CLICK)
                                : (right ? ClickItemAction.RIGHT_CLICK : ClickItemAction.LEFT_CLICK),
                        null,Collections.<Integer,HashedStack>emptyMap());
                ready = false; pending = true;
            }
            case "anvilRename" -> {
                packet = new ServerboundRenameItemPacket(step.text("name")); ready = false; pending = true;
            }
            case "anvilClose" -> {
                packet = new ServerboundContainerClosePacket(container);
                add(closes,row((MinecraftPacket)packet,Map.of("openSequence",openSequence,"containerId",container,"source","client")));
                container = -1; ready = false; pending = false;
            }
            default -> throw new IllegalArgumentException("Unknown anvil action");
        }
        return new Action(packet,evidence);
    }
    /** Discards world-specific menu authority without concealing a pending transaction; keeps historical observations. */
    void reset() { check(!pending,"World changed with unsettled anvil action"); container = -1; ready = false; }
    /** Rejects terminal connection actions while an anvil click or rename awaits a full refresh. */
    void requireSettled() { check(!pending,"Anvil action awaits full resynchronization"); }
    /** Reports whether any actual ANVIL open was received, controlling optional receipt inclusion. */
    boolean observed() { return !opens.isEmpty(); }
    /** Copies independent open, snapshot, cost, XP and close histories for strict replay. */
    Map<String,Object> report() { return Map.of("opens",List.copyOf(opens),"snapshots",List.copyOf(snapshots),"costs",List.copyOf(costs),"xp",List.copyOf(xp),"closes",List.copyOf(closes)); }
    /** Appends a receipt row with a separate 256-row bound for each observation category. */
    private static void add(List<Map<String,Object>> rows, Map<String,Object> row) {
        check(rows.size() < 256,"Anvil observation bound exceeded"); rows.add(row);
    }
    /**
     * Adds packet-body provenance and the observation time. The legacy field name
     * {@code receivedAtEpochMs} also timestamps preparation of an explicitly client-sourced close.
     */
    private static Map<String,Object> row(MinecraftPacket packet, Map<String,Object> fields) {
        var row = new LinkedHashMap<>(fields); row.put("receivedAtEpochMs",System.currentTimeMillis()); row.put("sha256",digest(packet)); return Map.copyOf(row);
    }
    /** Uses a common pinned item encoding so snapshots can compare identity/component bytes. */
    private static String itemDigest(ItemStack item) { return digest(new ClientboundSetCursorItemPacket(item)); }
    /** Hashes pinned serialized packet-body bytes and always releases the temporary buffer. */
    private static String digest(MinecraftPacket packet) {
        var bytes = Unpooled.buffer();
        try {
            packet.serialize(bytes); byte[] data = new byte[bytes.readableBytes()]; bytes.readBytes(data);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        finally { bytes.release(); }
    }
    /** Uses the session failure mechanism for invalid anvil state or evidence bounds. */
    private static void check(boolean condition,String reason) { ActionSession.check(condition,reason); }
}
