package com.kaveenk.onlydragons.playerclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

/**
 * Connection-local, server-authoritative view of the 46-slot player inventory.
 * The owning {@link ActionSession} serializes access under its monitor. Each click
 * consumes a fresh full snapshot; only a later full snapshot settles that click.
 * Unchanged-slot confirmations may advance a ready snapshot's observed state ID,
 * but cannot restore invalidated authority. No component prediction hashes are invented.
 */
final class InventoryState {
    /**
     * A real click request and the received state that authorized its construction.
     * @param packet container-0 packet with absent optimistic item predictions
     * @param evidence immutable snapshot/confirmation identity for independent receipt replay
     */
    record Click(ServerboundContainerClickPacket packet, Map<String, Object> evidence) {}
    private final List<Map<String, Object>> snapshots = new ArrayList<>();
    private final List<Map<String, Object>> confirmations = new ArrayList<>();
    private ItemStack[] items;
    private String[] itemDigests;
    private String cursorDigest;
    private int stateId, openContainer;
    private int confirmationSequence;
    private boolean ready, pending;
    private String unavailable = "No full player-inventory snapshot received";

    /**
     * Consumes full snapshots or invalidates authority on changed/stateless deltas and menu transitions.
     * A ready snapshot accepts only byte-identical slot confirmations at equal or next state
     * ID modulo 32768. Histories preserve original packet/item hashes for replay.
     */
    void receive(Packet packet) {
        if (packet instanceof ClientboundContainerSetContentPacket contents && contents.getContainerId() == 0) {
            ActionSession.check(contents.getItems().length == 46, "Expected full 46-slot player inventory");
            ActionSession.check(contents.getStateId() >= 0 && contents.getStateId() <= 32767, "Invalid inventory state ID");
            ActionSession.check(snapshots.size() < 128, "Inventory snapshot capture limit exceeded");
            items = contents.getItems().clone(); stateId = contents.getStateId();
            itemDigests = Arrays.stream(items).map(InventoryState::itemDigest).toArray(String[]::new);
            cursorDigest = itemDigest(contents.getCarriedItem()); confirmationSequence = 0;
            snapshots.add(Map.of("sequence", snapshots.size() + 1, "containerId", 0, "stateId", stateId,
                    "slotCount", 46, "slotSha256", List.of(itemDigests),
                    "receivedAtEpochMs", System.currentTimeMillis(), "sha256", digest(contents)));
            ready = openContainer == 0; pending = false;
            unavailable = ready ? "" : "Another container is open";
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot && slot.getContainerId() == 0) {
            // Pinned Paper sends a redundant offhand slot with an incremented
            // state ID immediately after every full refresh. Preserve the full
            // view only when this packet proves the slot bytes are unchanged.
            // A delta can never settle an outstanding click or restore a stale view.
            if (!ready || pending) return;
            int id = slot.getStateId();
            if (items == null || slot.getSlot() < 0 || slot.getSlot() >= items.length
                    || id < 0 || id > 32767 || (id != stateId && id != ((stateId + 1) & 32767))) {
                invalidate("Invalid or out-of-sequence ContainerSetSlot state " + id + " after " + stateId);
            } else if (!itemDigests[slot.getSlot()].equals(itemDigest(slot.getItem()))) {
                invalidate("ContainerSetSlot changed slot " + slot.getSlot() + " after full snapshot " + snapshots.size());
            } else {
                ActionSession.check(confirmations.size() < 512, "Inventory confirmation capture limit exceeded");
                stateId = id; confirmationSequence = confirmations.size() + 1;
                confirmations.add(Map.of("sequence", confirmationSequence, "inventorySnapshotSequence", snapshots.size(),
                        "containerId", 0, "stateId", id, "slot", slot.getSlot(),
                        "itemSha256", itemDigests[slot.getSlot()],
                        "receivedAtEpochMs", System.currentTimeMillis(), "sha256", digest(slot)));
            }
        } else if (packet instanceof ClientboundSetCursorItemPacket carried) {
            if (!Objects.equals(cursorDigest, itemDigest(carried.getContents()))) invalidate("SetCursorItem changed carried item");
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket) {
            invalidate("SetPlayerInventory delta has no container state ID");
        } else if (packet instanceof ClientboundOpenScreenPacket opened) {
            openContainer = opened.getContainerId(); invalidate("Another container opened");
        } else if (packet instanceof ClientboundContainerClosePacket) {
            openContainer = 0; invalidate("Container closed before a full player-inventory refresh");
        }
    }

    /**
     * Consumes one ready full snapshot for an admitted equipment/storage slot 5-45.
     * Uses the latest confirmed received state ID with empty changed-slot and cursor
     * predictions. Paper owns the transaction; a companion event/state assertion proves it.
     */
    Click click(ActionPlan.Step step) {
        ActionSession.check(ready && !pending && items != null && openContainer == 0,
                "Inventory click requires a fresh full player-inventory snapshot: " + unavailable);
        int slot = step.integer("slot");
        ActionSession.check(slot >= 5 && slot <= 45, "Inventory slot outside admitted equipment/storage range");
        var snapshot = snapshots.getLast();
        var packet = new ServerboundContainerClickPacket(0, stateId, slot, ContainerActionType.CLICK_ITEM,
                step.text("button").equals("left") ? ClickItemAction.LEFT_CLICK : ClickItemAction.RIGHT_CLICK,
                null, Collections.<Integer, HashedStack>emptyMap());
        // These are intentionally absent predictions. The real server computes the
        // transaction and sends inventory/cursor state. No optimistic local edits.
        invalidate("Previous click awaits a full server resynchronization"); pending = true;
        return new Click(packet, Map.of("containerId", 0, "stateId", stateId,
                "inventorySnapshotSequence", snapshot.get("sequence"),
                "inventorySnapshotReceivedAtEpochMs", snapshot.get("receivedAtEpochMs"),
                "inventorySnapshotSha256", snapshot.get("sha256"), "inventoryConfirmationSequence", confirmationSequence));
    }
    /** Clears world-specific inventory authority, refusing to hide an outstanding click; evidence history remains. */
    void reset() {
        ActionSession.check(!pending, "World changed before inventory resynchronization");
        items = null; itemDigests = null; cursorDigest = null; openContainer = 0;
        invalidate("World changed before a new full player-inventory snapshot");
    }
    /** Rejects terminal disconnect/reconnect while a submitted click still awaits a full snapshot. */
    void requireSettled() { ActionSession.check(!pending, "Inventory click has not received a full server resynchronization"); }
    /** Records a client-side menu close and invalidates player-window authority until a fresh full snapshot. */
    void clientClosedContainer() {
        ActionSession.check(openContainer > 0 && !pending, "No settled container to close");
        openContainer = 0; invalidate("Client close awaits full player-inventory resynchronization");
    }
    /** Returns an immutable list of full-snapshot receipt rows in connection receive order. */
    List<Map<String, Object>> snapshots() { return List.copyOf(snapshots); }
    /** Returns immutable unchanged-slot confirmation rows without rewriting their parent snapshots. */
    List<Map<String, Object>> confirmations() { return List.copyOf(confirmations); }
    /** Removes click readiness while preserving the diagnostic and any outstanding resynchronization obligation. */
    private void invalidate(String reason) { ready = false; unavailable = reason; }
    /** Hashes the same pinned item serialization for full-snapshot slots, cursor and slot-confirmation comparisons. */
    private static String itemDigest(ItemStack item) { return digest(new ClientboundSetCursorItemPacket(item)); }
    /** Hashes serialized packet-body bytes with SHA-256 and releases the temporary Netty buffer on every path. */
    private static String digest(MinecraftPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.serialize(buffer);
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        finally { buffer.release(); }
    }
}
