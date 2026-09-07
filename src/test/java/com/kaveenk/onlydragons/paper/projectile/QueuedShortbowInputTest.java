package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression controls for next-tick input using synthetic clicks and an exact 20-arrow inventory oracle. Cancellation/identity invalidation is tested without asserting a native held-use loop; sustained firing is separately tested on Paper.
 */
class QueuedShortbowInputTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock player;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class); player = server.addPlayer();
        plugin.bows().openEncounter(UUID.randomUUID(), player.getWorld(), new BoundingBox(-1000,-1000,-1000,1000,1000,1000), new MechanicRevision("test","v1"));
        player.getInventory().setItemInMainHand(plugin.equipment().createLoadout("volley_shortbow_v4"));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 20));
    }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void close() { MockBukkit.unmock(); }
    /**
     * Injects one allowed main-hand click; no actual client button or native draw is represented.
     */
    void queue() {
        var input = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, player.getInventory().getItemInMainHand(), null, null, EquipmentSlot.HAND);
        input.setUseItemInHand(Event.Result.ALLOW); plugin.bows().interact(input);
    }
    /**
     * Advances two mock ticks and requires the original 20 arrows and zero capacity/groups.
     */
    void unchanged() {
        server.getScheduler().performTicks(2);
        assertEquals(20, player.getInventory().getItem(9).getAmount());
        assertEquals(0, plugin.bows().capacityUsed()); assertEquals(0, plugin.bows().pendingGroups());
    }
    /**
     * A stop signal before the next tick leaves ammo, capacity and pending groups unchanged.
     */
    @Test void releaseClearsQueuedInputBeforeItCanFire() { queue(); plugin.bows().stopUsing(player.getUniqueId()); unchanged(); }
    /**
     * A different weapon instance cannot reuse a queued click captured for the previous bow.
     */
    @Test void replacementWeaponCannotInheritQueuedClick() {
        queue(); player.getInventory().setItemInMainHand(plugin.equipment().createLoadout("swift_shortbow_v4")); unchanged();
    }
    /**
     * Full item contents, not UUID alone, invalidate queued input after an enchant edit.
     */
    @Test void sameUuidEnchantEditCannotInheritQueuedClick() {
        var registry = CalibrationLoadouts.compatibleRegistry(); var codec = new WeaponItemCodec(registry);
        var original = registry.create("volley_shortbow_v4"); player.getInventory().setItemInMainHand(codec.encode(original));
        queue(); player.getInventory().setItemInMainHand(codec.encode(registry.edit(original, Map.of("dragon_tracer",5), List.of()))); unchanged();
    }
    /**
     * Two slot-change events invalidate queued use even when the final selected weapon appears unchanged.
     */
    @Test void slotRoundTripClearsQueuedInputEvenWithSameWeaponRestored() {
        queue(); server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 0, 1));
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 1, 0)); unchanged();
    }
}
