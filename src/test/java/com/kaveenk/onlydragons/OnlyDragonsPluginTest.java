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

class OnlyDragonsPluginTest {
    private ServerMock server;
    private OnlyDragonsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OnlyDragonsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock player() {
        PlayerMock player = server.addPlayer("Alex");
        while (player.nextMessage() != null) {
            /* Discard join messages before command tests. */ }
        return player;
    }

    @Test
    void enablesWithDefaultConfigurationAndDisablesCleanly() {
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.getConfig().getBoolean("welcome-enabled"));
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());
    }

    @Test
    void statusWorksForAnOrdinaryPlayer() {
        var player = player();
        assertTrue(server.dispatchCommand(player, "onlydragons status"));
        assertTrue(player.nextMessage().startsWith("OnlyDragons ready | version "));
    }

    @Test
    void aliasAndDefaultActionWork() {
        var player = player();
        assertTrue(server.dispatchCommand(player, "mcdev"));
        assertTrue(player.nextMessage().contains("OnlyDragons ready"));
    }

    @Test
    void ordinaryPlayerCannotReloadConfiguration() {
        var player = player();
        plugin.getConfig().set("welcome-message", "Unsaved value");
        server.dispatchCommand(player, "onlydragons reload");
        assertEquals("You do not have permission to reload OnlyDragons.", player.nextMessage());
        assertEquals("Unsaved value", plugin.getConfig().getString("welcome-message"));
    }

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

    @Test
    void welcomeMessageIsDeliveredByRegisteredListener() {
        var player = player();
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        assertEquals("Welcome, Alex! Your development plugin is running.", player.nextMessage());
    }

    @Test
    void completionRespectsPermissionsAndPrefix() {
        var player = player();
        var command = plugin.getCommand("onlydragons");
        assertNotNull(command);
        assertEquals(List.of("status", "stats", "combat"), command.tabComplete(player, "onlydragons", new String[] { "" }));
        player.setOp(true);
        assertEquals(List.of("reload"), command.tabComplete(player, "onlydragons", new String[] { "re" }));
        assertEquals(List.of(), command.tabComplete(player, "onlydragons", new String[] { "reload", "" }));
    }

    @Test
    void unknownSubcommandShowsUsage() {
        var player = player();
        server.dispatchCommand(player, "onlydragons unknown");
        assertEquals("Usage: /onlydragons [status|reload|stats|combat|dev]", player.nextMessage());
    }
}
