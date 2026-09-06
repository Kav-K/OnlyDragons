package com.kaveenk.onlydragons.paper.encounter;

import static org.junit.jupiter.api.Assertions.*;
import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class ArenaConfigurationTest {
    @TempDir Path dir;
    ServerMock server;
    OnlyDragonsPlugin plugin;
    @BeforeEach void start() { server = MockBukkit.mock(); server.addSimpleWorld("world"); plugin = MockBukkit.load(OnlyDragonsPlugin.class); }
    @AfterEach void stop() { MockBukkit.unmock(); }
    DevelopmentArena valid() { return new DevelopmentArena(server.getWorlds().getFirst().getKey().toString(), 0, 100, 0, 24, "test_dragon"); }
    @Test void freshAndLegacyAreUnconfiguredWithoutWorldFallback() throws Exception {
        Path file = dir.resolve("config.yml"); Files.writeString(file, "welcome-enabled: false\nlegacy-value: retained\n");
        var config = new ArenaConfiguration(file, plugin.dragonDefinitions());
        assertTrue(config.current().isEmpty()); assertTrue(config.problem().contains("unconfigured"));
        config.save(valid());
        assertTrue(Files.readString(file).contains("legacy-value: retained"));
        var restarted = new ArenaConfiguration(file, plugin.dragonDefinitions());
        assertEquals(config.current(), restarted.current());
    }
    @Test void invalidCandidateAndPersistenceFailureRetainPrevious() throws Exception {
        Path file = dir.resolve("config.yml"); Files.writeString(file, "welcome-enabled: false\n");
        var config = new ArenaConfiguration(file, plugin.dragonDefinitions()); config.save(valid());
        byte[] saved = Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class, () -> config.save(new DevelopmentArena("missing:world", 0, 100, 0, 24, "test_dragon")));
        assertArrayEquals(saved, Files.readAllBytes(file));
        Files.delete(file); Files.createDirectory(file);
        assertThrows(java.io.IOException.class, () -> config.save(valid())); assertEquals(valid(), config.current().orElseThrow());
    }
    @Test void failedAtomicReplacementLeavesReadableOriginalAndPreviousArena() throws Exception {
        Path file = dir.resolve("config.yml"); Files.writeString(file, "legacy: retained\n");
        new ArenaConfiguration(file, plugin.dragonDefinitions()).save(valid());
        byte[] original = Files.readAllBytes(file);
        var config = new ArenaConfiguration(file, plugin.dragonDefinitions(), (source, target) -> {
            assertTrue(Files.readString(source).contains("radius: 20.0"));
            throw new java.io.IOException("Injected atomic replacement failure");
        });
        var v = valid();
        assertThrows(java.io.IOException.class, () -> config.save(new DevelopmentArena(v.worldKey(), 0, 100, 0, 20, v.type())));
        assertArrayEquals(original, Files.readAllBytes(file)); assertEquals(v, config.current().orElseThrow());
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }
    @Test void malformedAndIncompleteConfigFailClosed() throws Exception {
        Path file = dir.resolve("config.yml");
        for (String text : java.util.List.of("development-arena: broken\n", "development-arena:\n  x: 0\n", "development-arena: [invalid")) {
            Files.writeString(file, text); var config = new ArenaConfiguration(file, plugin.dragonDefinitions());
            assertTrue(config.current().isEmpty()); assertTrue(config.problem().contains("invalid"));
        }
    }
    @Test void numericIdentifierHeightAndDefinitionBoundariesReject() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 30_000_000})
            assertThrows(IllegalArgumentException.class, () -> new DevelopmentArena("minecraft:overworld", value, 100, 0, 24, "test_dragon"));
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentArena("../world", 0, 100, 0, 24, "test_dragon"));
        for (double radius : new double[]{0, 15.99, 48.01}) assertThrows(IllegalArgumentException.class, () -> new DevelopmentArena("minecraft:overworld", 0, 100, 0, radius, "test_dragon"));
        var v = valid();
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentArena(v.worldKey(), 0, -1000, 0, 24, v.type()).validate(plugin.dragonDefinitions().snapshot()));
        assertThrows(IllegalArgumentException.class, () -> new DevelopmentArena(v.worldKey(), 0, 100, 0, 24, "unknown").validate(plugin.dragonDefinitions().snapshot()));
    }
    @Test void permissionDenialCannotWriteOrAdoptAndSpawnFailsClosed() throws Exception {
        var p = server.addPlayer();
        Path config = plugin.getDataFolder().toPath().resolve("config.yml"); byte[] before = Files.readAllBytes(config);
        server.dispatchCommand(p, "onlydragons dev dragon setup minecraft:overworld 0 100 0 24 test_dragon");
        assertArrayEquals(before, Files.readAllBytes(config)); assertTrue(plugin.dragons().arena().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> plugin.dragons().spawn());
        assertTrue(plugin.dragons().status().contains("IDLE")); assertEquals(0, plugin.combat().activeCount());
    }
}
