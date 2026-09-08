package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MockBukkit permission, atomic grant and help-routing tests for the declared shortbow kit. Exact inventory totals and registered completion/output are checked; cadence, held use and visible pullback require real Paper/client evidence.
 */
class ShortbowCommandTest {
    private ServerMock server;
    private OnlyDragonsPlugin plugin;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() { server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class); }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void close() { MockBukkit.unmock(); }

    /**
     * Denied players receive no kit and no completion; console player-only execution reports the boundary.
     */
    @Test void permissionAndPlayerOnlyBoundariesAreEnforced() {
        var player = server.addPlayer();
        server.dispatchCommand(player, "onlydragons dev shortbow kit");
        assertTrue(Arrays.stream(player.getInventory().getStorageContents()).allMatch(i -> i == null || i.getType().isAir()));
        assertFalse(plugin.getCommand("onlydragons").tabComplete(player, "onlydragons", new String[]{"dev", ""}).contains("shortbow"));
        server.dispatchCommand(server.getConsoleSender(), "onlydragons dev shortbow kit");
        assertEquals("This command requires a player.", server.getConsoleSender().nextMessage().replace("§7", ""));
    }

    /**
     * Insufficient capacity leaves the full snapshot unchanged; success retains old contents and grants declared identities plus exactly 512 arrows.
     */
    @Test void kitRejectsWithoutPartialGrantAndPreservesExistingContents() {
        var player = server.addPlayer(); player.setOp(true);
        for (int i = 0; i < 22; i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        var before = player.getInventory().getStorageContents();
        server.dispatchCommand(player, "onlydragons dev shortbow kit");
        assertArrayEquals(before, player.getInventory().getStorageContents());
        player.getInventory().setItem(21, null);
        server.dispatchCommand(player, "onlydragons dev shortbow kit");
        for (int i = 0; i < 21; i++) assertEquals(new ItemStack(Material.STONE, 64), player.getInventory().getItem(i));
        assertEquals(512, Arrays.stream(player.getInventory().getStorageContents()).filter(i -> i != null && i.getType() == Material.ARROW).mapToInt(ItemStack::getAmount).sum());
        for (int i = 0; i < 7; i++) {
            player.getInventory().setHeldItemSlot(0);
            var inspection = plugin.equipment().refresh(player.getUniqueId(), player.getInventory().getItem(21 + i), null);
            assertEquals(ShortbowLoadouts.ids().get(i), ((com.kaveenk.onlydragons.paper.item.codec.ItemReadResult.Valid)inspection.fingerprint().mainHand()).item().instance().identity().definitionId());
        }
        var granted = player.getInventory().getStorageContents();
        server.dispatchCommand(player, "onlydragons dev shortbow kit");
        assertArrayEquals(granted, player.getInventory().getStorageContents());
        assertEquals(List.of("list", "kit", "help"), plugin.getCommand("onlydragons").tabComplete(player, "onlydragons", new String[]{"dev", "shortbow", ""}));
    }

    /**
     * Checks the executable setup order and tier/proc text while requiring every declared loadout ID to be listed.
     */
    @Test void helpOrdersSetupKitSpawnThenSafePositionAndListLabelsGuarantee() {
        var sender = server.getConsoleSender();
        server.dispatchCommand(sender, "onlydragons dev shortbow help");
        var text = messages();
        assertTrue(text.indexOf("setup only if unconfigured") < text.indexOf("shortbow kit"));
        assertTrue(text.indexOf("shortbow kit") < text.indexOf("spawn training orbit"));
        assertTrue(text.indexOf("spawn training orbit") < text.indexOf("safe standing pad"));
        server.dispatchCommand(sender, "onlydragons dev shortbow list");
        text = messages();
        assertTrue(text.contains("5 ticks/trigger (4/s at 20 TPS)"));
        assertTrue(text.contains("2 ticks/trigger (10/s at 20 TPS)"));
        assertTrue(text.contains("one guaranteed extra hit"));
        assertTrue(text.contains("aimed Tracer V (v3: 20-block acquisition, 30-degree launch half-angle)"));
        assertFalse(text.contains("40-block"));
        for (String id : ShortbowLoadouts.ids()) assertTrue(text.contains(id));
    }

    /**
     * Drains console responses in order without replacing their diagnostic content.
     * @return newline-separated message text
     */
    private String messages() {
        StringBuilder result = new StringBuilder(); String message;
        while ((message = server.getConsoleSender().nextMessage()) != null) result.append(message).append('\n');
        return result.toString();
    }
}
