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

/** Regression controls for next-tick input; native sustained firing is tested separately on Paper. */
class QueuedShortbowInputTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock player;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class); player = server.addPlayer();
        plugin.bows().openEncounter(UUID.randomUUID(), player.getWorld(), new BoundingBox(-1000,-1000,-1000,1000,1000,1000), new MechanicRevision("test","v1"));
        player.getInventory().setItemInMainHand(plugin.equipment().createLoadout("volley_shortbow_v4"));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 20));
    }
    @AfterEach void close() { MockBukkit.unmock(); }
    void queue() {
        var input = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, player.getInventory().getItemInMainHand(), null, null, EquipmentSlot.HAND);
        input.setUseItemInHand(Event.Result.ALLOW); plugin.bows().interact(input);
    }
    void unchanged() {
        server.getScheduler().performTicks(2);
        assertEquals(20, player.getInventory().getItem(9).getAmount());
        assertEquals(0, plugin.bows().capacityUsed()); assertEquals(0, plugin.bows().pendingGroups());
    }
    @Test void releaseClearsQueuedInputBeforeItCanFire() { queue(); plugin.bows().stopUsing(player.getUniqueId()); unchanged(); }
    @Test void replacementWeaponCannotInheritQueuedClick() {
        queue(); player.getInventory().setItemInMainHand(plugin.equipment().createLoadout("swift_shortbow_v4")); unchanged();
    }
    @Test void sameUuidEnchantEditCannotInheritQueuedClick() {
        var registry = CalibrationLoadouts.compatibleRegistry(); var codec = new WeaponItemCodec(registry);
        var original = registry.create("volley_shortbow_v4"); player.getInventory().setItemInMainHand(codec.encode(original));
        queue(); player.getInventory().setItemInMainHand(codec.encode(registry.edit(original, Map.of("dragon_tracer",5), List.of()))); unchanged();
    }
    @Test void slotRoundTripClearsQueuedInputEvenWithSameWeaponRestored() {
        queue(); server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 0, 1));
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 1, 0)); unchanged();
    }
}
