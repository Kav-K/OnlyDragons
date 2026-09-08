package com.kaveenk.onlydragons;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;

/**
 * MockBukkit composition smoke tests for registered commands, permissions, configuration and the join listener. Explicitly dispatched events test registration/routing, not authenticated native login or real Paper lifecycle timing.
 */
class OnlyDragonsPluginTest {
    private ServerMock server;
    private OnlyDragonsPlugin plugin;

    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OnlyDragonsPlugin.class);
    }

    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Adds Alex and discards join messages so command assertions observe only their own response.
     * @return new mock player with an empty message queue
     */
    private PlayerMock player() {
        PlayerMock player = server.addPlayer("Alex");
        while (player.nextMessage() != null) {
            /* Discard join messages before command tests. */ }
        return player;
    }

    /**
     * Checks enable defaults and plugin-manager disable on the actual composition root.
     */
    @Test
    void enablesWithDefaultConfigurationAndDisablesCleanly() {
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.getConfig().getBoolean("welcome-enabled"));
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());
    }

    /**
     * Verifies ordinary players can reach the registered status handler.
     */
    @Test
    void statusWorksForAnOrdinaryPlayer() {
        var player = player();
        assertTrue(server.dispatchCommand(player, "onlydragons status"));
        assertTrue(player.nextMessage().startsWith("OnlyDragons ready | version "));
    }

    /**
     * Checks the configured alias routes its empty argument list to status.
     */
    @Test
    void aliasAndDefaultActionWork() {
        var player = player();
        assertTrue(server.dispatchCommand(player, "mcdev"));
        assertTrue(player.nextMessage().contains("OnlyDragons ready"));
    }

    /**
     * Denial retains an unsaved in-memory value, proving reload did not execute.
     */
    @Test
    void ordinaryPlayerCannotReloadConfiguration() {
        var player = player();
        plugin.getConfig().set("welcome-message", "Unsaved value");
        server.dispatchCommand(player, "onlydragons reload");
        assertEquals("You do not have permission to reload OnlyDragons.", player.nextMessage());
        assertEquals("Unsaved value", plugin.getConfig().getString("welcome-message"));
    }

    /**
     * Saves a changed template and verifies reload replaces the greeting service behavior.
     */
    @Test
    void operatorCanReloadPersistedConfiguration() {
        var player = player();
        player.setOp(true);
        plugin.getConfig().set("welcome-message", "New hello {player}");
        plugin.saveConfig();
        server.dispatchCommand(player, "onlydragons reload");
        assertEquals("OnlyDragons configuration reloaded.", player.nextMessage());
        assertEquals("New hello Alex", plugin.greetings().welcome("Alex"));
    }

    /**
     * Dispatches a synthetic join through the plugin manager and asserts the exact configured greeting.
     */
    @Test
    void welcomeMessageIsDeliveredByRegisteredListener() {
        var player = player();
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        assertEquals("Welcome, Alex! Your development plugin is running.", player.nextMessage());
    }

    /**
     * Compares exact completion lists before/after operator permission and for invalid argument depth.
     */
    @Test
    void completionRespectsPermissionsAndPrefix() {
        var player = player();
        var command = plugin.getCommand("onlydragons");
        assertNotNull(command);
        assertEquals(List.of("help", "status", "stats", "combat"), command.tabComplete(player, "onlydragons", new String[] { "" }));
        player.setOp(true);
        assertEquals(List.of("reload"), command.tabComplete(player, "onlydragons", new String[] { "re" }));
        assertEquals(List.of(), command.tabComplete(player, "onlydragons", new String[] { "reload", "" }));
    }

    /**
     * Checks unknown input returns the registered root usage rather than executing another handler.
     */
    @Test
    void unknownSubcommandShowsUsage() {
        var player = player();
        server.dispatchCommand(player, "onlydragons unknown");
        assertEquals("Usage: /onlydragons [help [topic]|status|reload|stats|combat|dragon|bow|book|practice|dev]", player.nextMessage());
    }
}
