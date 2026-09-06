package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.playerclient.ActionPlanTest.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.junit.jupiter.api.Test;

class InventoryStateTest {
    static ActionPlan.Step click(int slot, String button) throws Exception {
        return parse(plan(step("click", "inventoryClick", "{\"slot\":" + slot + ",\"button\":\"" + button + "\"}") + "," + exit()))
                .actors().getFirst().sessions().getFirst().steps().getFirst();
    }
    static ItemStack tagged(String identity) {
        var data = new DataComponents(new HashMap<>());
        data.put(DataComponentTypes.CUSTOM_DATA, NbtMap.builder().putString("onlydragons:item", identity).build());
        return new ItemStack(100, 1, data);
    }
    static ClientboundContainerSetContentPacket snapshot(int state, int slot, ItemStack item, ItemStack cursor) {
        var items = new ItemStack[46]; items[slot] = item;
        return new ClientboundContainerSetContentPacket(0, state, items, cursor);
    }

    @Test void taggedItemsUseExactObservedStateWithNoInventedComponentPrediction() throws Exception {
        var view = new InventoryState(); var item = tagged("instance-one");
        view.receive(snapshot(17, 37, item, null));
        var click = view.click(click(37, "left")); var packet = click.packet();
        assertEquals(0, packet.getContainerId()); assertEquals(17, packet.getStateId()); assertEquals(37, packet.getSlot());
        assertEquals(ContainerActionType.CLICK_ITEM, packet.getAction()); assertEquals(ClickItemAction.LEFT_CLICK, packet.getParam());
        assertNull(packet.getCarriedItem()); assertTrue(packet.getChangedSlots().isEmpty());
        assertEquals(1, click.evidence().get("inventorySnapshotSequence"));
        assertEquals(view.snapshots().getFirst().get("sha256"), click.evidence().get("inventorySnapshotSha256"));
        ByteBuf wire = Unpooled.buffer();
        try { packet.serialize(wire); assertEquals(packet, new ServerboundContainerClickPacket(wire)); assertEquals(0, wire.readableBytes()); }
        finally { wire.release(); }
        var different = new InventoryState(); different.receive(snapshot(17, 37, tagged("instance-two"), null));
        assertNotEquals(view.snapshots().getFirst().get("sha256"), different.snapshots().getFirst().get("sha256"),
                "Received item component bytes must affect snapshot provenance");
    }
    @Test void eachClickNeedsNewFullSnapshotAndCannotCloseWhileUnsettled() throws Exception {
        var view = new InventoryState(); var item = tagged("one");
        view.receive(snapshot(17, 37, item, null)); view.click(click(37, "left"));
        assertThrows(IllegalStateException.class, () -> view.click(click(40, "left")));
        assertThrows(IllegalStateException.class, view::requireSettled);
        view.receive(new ClientboundContainerSetSlotPacket(0, 18, 37, null));
        view.receive(new ClientboundSetCursorItemPacket(item));
        assertThrows(IllegalStateException.class, () -> view.click(click(40, "left")), "Partial deltas cannot acknowledge full resynchronization");
        view.receive(snapshot(19, 37, null, item)); view.requireSettled();
        var second = view.click(click(40, "right"));
        assertEquals(2, second.evidence().get("inventorySnapshotSequence"));
        assertEquals(19, second.packet().getStateId()); assertEquals(ClickItemAction.RIGHT_CLICK, second.packet().getParam());
    }
    @Test void paperFullRefreshThenUnchangedOffhandConfirmsLatestStateWithoutReplacingSnapshot() throws Exception {
        var view = new InventoryState();
        var item = tagged("managed-bow");
        view.receive(snapshot(52, 37, item, null));
        var offhand = new ClientboundContainerSetSlotPacket(0, 53, 45, null);
        ByteBuf wire = Unpooled.buffer();
        try {
            offhand.serialize(wire);
            view.receive(new ClientboundContainerSetSlotPacket(wire));
            assertEquals(0, wire.readableBytes());
        } finally { wire.release(); }
        var submitted = view.click(click(37, "left"));
        assertEquals(53, submitted.packet().getStateId(), "Use the state actually received after Paper's full refresh");
        assertEquals(1, submitted.evidence().get("inventorySnapshotSequence"));
        assertEquals(1, submitted.evidence().get("inventoryConfirmationSequence"));
        assertEquals(52, view.snapshots().getFirst().get("stateId"), "Do not rewrite received snapshot provenance");
        var confirmation = view.confirmations().getFirst();
        assertEquals(53, confirmation.get("stateId")); assertEquals(45, confirmation.get("slot"));
        assertEquals(1, confirmation.get("inventorySnapshotSequence"));
        assertEquals(64, ((String) confirmation.get("sha256")).length());
        var slotDigests = (java.util.List<?>) view.snapshots().getFirst().get("slotSha256");
        assertEquals(46, slotDigests.size());
        assertEquals(slotDigests.get(45), confirmation.get("itemSha256"));
        assertNotEquals(slotDigests.get(37), confirmation.get("itemSha256"));
        view.receive(new ClientboundContainerSetSlotPacket(0, 54, 45, null));
        assertEquals(1, view.confirmations().size(), "A delta cannot acknowledge the click or restore readiness");
        assertThrows(IllegalStateException.class, view::requireSettled);
        assertThrows(IllegalStateException.class, () -> view.click(click(40, "left")));
        view.receive(snapshot(55, 37, null, item));
        assertEquals(0, view.click(click(40, "left")).evidence().get("inventoryConfirmationSequence"));
    }
    @Test void onlyIdenticalItemBytesCanConfirmAndChangedThenRestoredDeltaStaysInvalid() throws Exception {
        var original = tagged("one");
        var view = new InventoryState(); view.receive(snapshot(10, 37, original, null));
        view.receive(new ClientboundContainerSetSlotPacket(0, 11, 37, tagged("one")));
        view.receive(new ClientboundContainerSetSlotPacket(0, 12, 45, null));
        assertEquals(12, view.click(click(37, "left")).packet().getStateId());
        for (ItemStack replacement : new ItemStack[]{tagged("different-identity"), new ItemStack(original.getId(), 2, original.getDataComponentsPatch()), null}) {
            var changed = new InventoryState(); changed.receive(snapshot(10, 37, original, null));
            changed.receive(new ClientboundContainerSetSlotPacket(0, 11, 37, replacement));
            changed.receive(new ClientboundContainerSetSlotPacket(0, 12, 37, original));
            assertTrue(changed.confirmations().isEmpty());
            var error = assertThrows(IllegalStateException.class, () -> changed.click(click(37, "left")));
            assertTrue(error.getMessage().contains("changed slot 37"));
        }
    }
    @Test void confirmationSequenceIsBoundedValidAndWrapsOnlyAtProtocolBoundary() throws Exception {
        var item = tagged("one");
        var wrapped = new InventoryState(); wrapped.receive(snapshot(32767, 37, item, null));
        wrapped.receive(new ClientboundContainerSetSlotPacket(0, 0, 45, null));
        assertEquals(0, wrapped.click(click(37, "left")).packet().getStateId());
        for (int invalid : new int[]{-1, 32768, 12, 9}) {
            var view = new InventoryState(); view.receive(snapshot(10, 37, item, null));
            view.receive(new ClientboundContainerSetSlotPacket(0, invalid, 45, null));
            assertTrue(view.confirmations().isEmpty());
            assertThrows(IllegalStateException.class, () -> view.click(click(37, "left")));
        }
        var bounded = new InventoryState(); bounded.receive(snapshot(0, 37, item, null));
        for (int state = 1; state <= 512; state++) bounded.receive(new ClientboundContainerSetSlotPacket(0, state, 45, null));
        assertThrows(IllegalStateException.class, () -> bounded.receive(new ClientboundContainerSetSlotPacket(0, 513, 45, null)));
    }
    @Test void missingMalformedOrOtherWindowStateCannotDrivePlayerClicks() throws Exception {
        var view = new InventoryState();
        assertThrows(IllegalStateException.class, () -> view.click(click(37, "left")));
        assertThrows(IllegalStateException.class, () -> view.receive(new ClientboundContainerSetContentPacket(0, 1, new ItemStack[45], null)));
        view.receive(new ClientboundContainerSetContentPacket(3, 1, new ItemStack[46], null));
        assertThrows(IllegalStateException.class, () -> view.click(click(37, "left")));
        view.receive(snapshot(2, 37, tagged("one"), null));
        view.receive(new ClientboundContainerClosePacket(0));
        assertThrows(IllegalStateException.class, () -> view.click(click(37, "left")));
    }
    @Test void deltaAndWorldChangeInvalidateStateButWrappedIdsNeedNoGuessing() throws Exception {
        var view = new InventoryState(); var item = tagged("one");
        view.receive(snapshot(32767, 37, item, null));
        view.receive(new ClientboundSetPlayerInventoryPacket(1, item));
        assertThrows(IllegalStateException.class, () -> view.click(click(37, "left")));
        view.receive(snapshot(0, 37, item, null));
        assertEquals(0, view.click(click(37, "left")).packet().getStateId());
        assertThrows(IllegalStateException.class, view::reset);
        view.receive(snapshot(1, 37, null, item)); view.reset();
        assertThrows(IllegalStateException.class, () -> view.click(click(40, "left")));
    }
    @Test void slotButtonTypesAndUnsupportedCraftingSlotsReject() {
        for (String args : new String[]{"{\"slot\":4,\"button\":\"left\"}", "{\"slot\":46,\"button\":\"left\"}",
                "{\"slot\":true,\"button\":\"left\"}", "{\"slot\":37.0,\"button\":\"left\"}",
                "{\"slot\":37,\"button\":\"shift\"}", "{\"slot\":37,\"button\":\"left\",\"stateId\":17}"}) {
            assertThrows(Exception.class, () -> parse(plan(step("click", "inventoryClick", args) + "," + exit())));
        }
    }
}
