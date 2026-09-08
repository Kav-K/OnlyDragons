package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import java.util.List;
import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/** Registered command behavior: discovery, sender boundaries, route isolation and atomic grants. */
class CommandGuideTest {
    private ServerMock server;
    private OnlyDragonsPlugin plugin;
    private PlayerMock player;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        player = server.addPlayer(); messages();
    }
    @AfterEach void close() { MockBukkit.unmock(); }
    private void command(String args) { server.dispatchCommand(player, "onlydragons " + args); }
    private String messages() {
        StringBuilder result = new StringBuilder(); String next;
        while ((next = player.nextMessage()) != null) result.append(next).append('\n');
        return result.toString();
    }
    private List<String> complete(String... args) { return plugin.getCommand("onlydragons").tabComplete(player, "onlydragons", args); }
    private int count(Material material) {
        return Arrays.stream(player.getInventory().getStorageContents()).filter(i -> i != null && i.getType() == material).mapToInt(ItemStack::getAmount).sum();
    }
    @Test void helpFiltersTopicsAndConsoleActionsAndHandlesUnknownDepth() {
        command("help"); var text = messages();
        assertTrue(text.contains("help, status, stats, combat")); assertFalse(text.contains("bow"));
        command("help bow"); assertTrue(messages().contains("No available help"));
        player.setOp(true); command("help bow"); text = messages();
        assertTrue(text.contains("bow give <id>")); assertTrue(text.contains("15 empty storage slots"));
        assertTrue(text.contains("[player only]")); assertFalse(text.contains("dev bonus"));
        command("help dev"); assertTrue(messages().contains("dev bonus <stat>"));
        command("help altar"); assertTrue(messages().contains("No available help"));
        command("help bow extra"); assertTrue(messages().contains("Usage: /onlydragons help [topic]"));
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "onlydragons help bow");
        StringBuilder consoleText = new StringBuilder(); String next;
        while ((next = console.nextMessage()) != null) consoleText.append(next);
        assertTrue(consoleText.toString().contains("[player/console]"));
        assertFalse(consoleText.toString().contains("bow kit"));
        assertFalse(consoleText.toString().contains("bow give"));
    }
    @Test void grantsRemainDistinctAndPermissionsAreIndependent() {
        command("bow give ordinary"); command("bow kit"); command("practice kit ordinary"); command("book power 1");
        assertTrue(player.getInventory().isEmpty());
        var calibration = player.addAttachment(plugin, "onlydragons.calibration", true);
        command("practice kit ordinary"); assertTrue(player.getInventory().isEmpty());
        command("bow give ordinary"); assertEquals(1, count(Material.BOW)); assertEquals(0, count(Material.ARROW));
        player.getInventory().clear(); command("bow kit"); assertEquals(7, count(Material.BOW)); assertEquals(512, count(Material.ARROW));
        player.getInventory().clear(); calibration.remove();
        player.addAttachment(plugin, "onlydragons.practice", true);
        command("bow give ordinary"); assertTrue(player.getInventory().isEmpty());
        command("practice kit ordinary"); assertEquals(1, count(Material.BOW)); assertEquals(64, count(Material.ARROW));
    }
    @Test void aliasesPreserveValidationAndRejectCrossNamespaceActions() {
        player.setOp(true);
        command("practice bonus ferocity 99"); assertTrue(messages().contains("practice kit"));
        assertEquals(0, plugin.equipment().refresh(player).stats().snapshot().raw(com.kaveenk.onlydragons.domain.stats.StatKey.FEROCITY));
        command("practice dummy full NaN"); assertTrue(messages().contains("HP must be"));
        assertEquals(0, plugin.combat().activeCount());
        command("dragon spawn invalid"); assertTrue(messages().contains("Mode must be"));
        command("book power 999"); assertTrue(messages().contains("Invalid book request"));
        assertTrue(player.getInventory().isEmpty());
        command("book power 1"); assertEquals(1, count(Material.ENCHANTED_BOOK));
        command("bow give missing"); assertTrue(messages().contains("Invalid calibration request"));
        player.getInventory().clear();
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        var before = player.getInventory().getStorageContents();
        command("bow give ordinary"); command("bow kit"); command("practice kit ordinary"); command("book power 1");
        assertArrayEquals(before, player.getInventory().getStorageContents());
    }
    @Test void completionMatchesVisibilityRetainsCatalogsAndNeverFallsBack() {
        assertEquals(List.of("help", "status", "stats", "combat"), complete("help", ""));
        assertEquals(List.of(), complete("bow", "")); player.setOp(true);
        assertEquals(List.of("help", "list", "kit", "give"), complete("bow", ""));
        assertEquals(complete("dev", "loadout", ""), complete("bow", "give", ""));
        assertEquals(complete("dev", "book", "power", ""), complete("book", "power", ""));
        assertFalse(complete("book", "power", "").isEmpty());
        assertEquals(complete("dev", "dragon", "spawn", ""), complete("dragon", "spawn", ""));
        assertEquals(List.of("kit", "dummy", "scenario", "reset"), complete("practice", ""));
        assertEquals(List.of(), complete("practice", "bonus", ""));
        assertEquals(List.of(), complete("unknown", ""));
        assertEquals(complete("dev", "").size(), complete("dev", "").stream().distinct().count());
        var console = server.getConsoleSender();
        assertEquals(List.of("help", "list"), plugin.getCommand("onlydragons").tabComplete(console, "onlydragons", new String[]{"bow", ""}));
        assertEquals(List.of(), plugin.getCommand("onlydragons").tabComplete(console, "onlydragons", new String[]{"book", ""}));
    }
}
