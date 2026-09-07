package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Small operator configuration transaction: validate, atomically replace, then adopt.
 * Preserves unrelated YAML settings and never partially calls saveConfig. Construction
 * records load problems instead of inventing an arena. This synchronous boundary reads
 * and writes disk and resolves live worlds, so callers use the server thread only for
 * explicit setup/startup work, not a per-tick loop.
 */
public final class ArenaConfiguration {
    /**
     * Injectable same-filesystem atomic replacement boundary for persistence-failure tests.
     * Implementations must not publish a partially written target.
     */
    @FunctionalInterface interface Replace { /** Replaces the destination with the prepared candidate atomically or throws before reporting success.
 * @param source prepared temporary file
 * @param target persistent configuration destination
 * @throws IOException if replacement fails
 */ void move(Path source, Path target) throws IOException; }
    private final Replace replace;
    private final Path file;
    private final DragonDefinitionRegistry definitions;
    private DevelopmentArena current;
    private String problem = "Arena unconfigured; use dev dragon setup.";
    /**
     * Loads the configured arena, retaining an unconfigured/error diagnostic on failure.
     * @param file existing plugin config.yml; its parent must exist for later save
     * @param definitions trusted type registry used for live candidate validation
     */
    public ArenaConfiguration(Path file, DragonDefinitionRegistry definitions) {
        this(file, definitions, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    /**
     * Loads with an explicit atomic-replace boundary for deterministic failure tests.
     * @param file source/target configuration path
     * @param definitions trusted type registry
     * @param replace replacement action; production supplies atomic replace-existing move
     */
    ArenaConfiguration(Path file, DragonDefinitionRegistry definitions, Replace replace) {
        this.file = file; this.definitions = definitions; this.replace = replace;
        try {
            var yaml = read();
            if (!yaml.contains("development-arena")) return;
            var s = yaml.getConfigurationSection("development-arena");
            if (s == null || !s.getKeys(false).equals(java.util.Set.of("world", "x", "y", "z", "radius", "type")))
                throw new IllegalArgumentException("Incomplete or unknown development-arena keys.");
            for (String key : java.util.List.of("x", "y", "z", "radius"))
                if (!(s.get(key) instanceof Number)) throw new IllegalArgumentException("Invalid arena " + key);
            var candidate = new DevelopmentArena(s.getString("world"), s.getDouble("x"), s.getDouble("y"),
                    s.getDouble("z"), s.getDouble("radius"), s.getString("type"));
            candidate.validate(definitions.snapshot()); current = candidate; problem = "";
        } catch (Exception failure) { problem = "Arena configuration invalid: " + failure.getMessage(); }
    }
    /**
     * Reads the whole existing YAML so setup cannot silently erase legacy settings.
     * @return parsed mutable candidate
     * @throws IOException for unreadable files or malformed YAML
     */
    private YamlConfiguration read() throws IOException {
        var yaml = new YamlConfiguration();
        try { yaml.load(file.toFile()); }
        catch (org.bukkit.configuration.InvalidConfigurationException failure) { throw new IOException("Malformed config.yml", failure); }
        return yaml;
    }
    /**
     * Returns the last successfully loaded or persisted arena.
     * @return empty while unconfigured/invalid at startup; never guesses coordinates
     */
    public Optional<DevelopmentArena> current() { return Optional.ofNullable(current); }
    /**
     * Explains the initial configuration state for operator status.
     * @return empty after successful adoption, otherwise an unconfigured/load diagnostic
     */
    public String problem() { return problem; }
    /**
     * Validates a whole candidate, stages beside config.yml and atomically replaces it.
     * In-memory adoption follows successful replacement and temporary-file cleanup.
     * Failures before replacement retain the existing file/current arena; a failure
     * after replacement is surfaced rather than claiming a successful adoption.
     * @param candidate explicit arena whose world/type must currently validate
     * @throws IllegalArgumentException if candidate world, bounds or type are invalid
     * @throws IOException for read/write/atomic-replace/temporary-cleanup failures
     */
    public void save(DevelopmentArena candidate) throws IOException {
        candidate.validate(definitions.snapshot());
        var yaml = read(); // Preserve legacy/unrelated settings and reject malformed existing files.
        yaml.set("development-arena", null);
        yaml.set("development-arena.world", candidate.worldKey());
        yaml.set("development-arena.x", candidate.x()); yaml.set("development-arena.y", candidate.y());
        yaml.set("development-arena.z", candidate.z()); yaml.set("development-arena.radius", candidate.radius());
        yaml.set("development-arena.type", candidate.type());
        Path temporary = Files.createTempFile(file.getParent(), "arena-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString());
            replace.move(temporary, file);
        } finally { Files.deleteIfExists(temporary); }
        current = candidate; problem = "";
    }
}
