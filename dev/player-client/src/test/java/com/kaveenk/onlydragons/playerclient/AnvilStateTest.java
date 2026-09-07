package com.kaveenk.onlydragons.playerclient;

import java.util.Map;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnvilStateTest {
    private ActionPlan.Step click(String button) { return new ActionPlan.Step("collect","anvilClick",Map.of("slot",2,"button",button)); }
    private void open(AnvilState state,int id) { state.receive(new ClientboundOpenScreenPacket(id,ContainerType.ANVIL,Component.text("Anvil"))); }
    private void full(AnvilState state,int id,int version) { state.receive(new ClientboundContainerSetContentPacket(id,version,new ItemStack[39],null)); }
    @Test void realOpenAndFreshFullStateAreRequiredForEveryGestureAndReplayRejects() {
        var state = new AnvilState();
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
        open(state,7);
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
        for (String button : new String[]{"left","right","shift-left","shift-right"}) {
            full(state,7,5);
            var action = state.action(click(button));
            var packet = assertInstanceOf(ServerboundContainerClickPacket.class,action.packet());
            assertEquals(7,packet.getContainerId()); assertEquals(5,packet.getStateId()); assertEquals(2,packet.getSlot());
            assertThrows(IllegalStateException.class,()->state.action(click(button)));
            assertThrows(IllegalStateException.class,state::requireSettled);
        }
        full(state,7,6); state.requireSettled();
    }
    @Test void wrongWindowShapeDeltasReopenAndCloseCannotReusePriorAuthority() {
        var state = new AnvilState(); open(state,7);
        assertThrows(IllegalStateException.class,()->state.receive(new ClientboundContainerSetContentPacket(7,1,new ItemStack[46],null)));
        full(state,7,1);
        state.receive(new ClientboundContainerSetSlotPacket(7,2,2,null));
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
        open(state,8); full(state,7,2);
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
        full(state,8,2);
        state.action(new ActionPlan.Step("close","anvilClose",Map.of()));
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
    }
    @Test void renameInvalidatesUntilAuthoritativeFullResyncAndOrdinaryMenusDoNotAuthorize() {
        var state = new AnvilState(); open(state,3); full(state,3,2);
        state.action(new ActionPlan.Step("name","anvilRename",Map.of("name","My bow")));
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
        full(state,3,3); assertDoesNotThrow(()->state.action(click("left")));
        state.receive(new ClientboundOpenScreenPacket(4,ContainerType.GENERIC_9X3,Component.text("Chest")));
        assertThrows(IllegalStateException.class,()->state.action(click("left")));
    }
    @Test void statelessPlayerInventoryUpdateInvalidatesAnvilDestination() {
        var state = new AnvilState(); open(state,3); full(state,3,2);
        state.receive(new ClientboundSetPlayerInventoryPacket(0,new ItemStack(1,1)));
        assertThrows(IllegalStateException.class,()->state.action(click("shift-left")));
        full(state,3,3); assertDoesNotThrow(()->state.action(click("shift-left")));
    }
}
