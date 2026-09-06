package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;

/** Tiny operator configuration transaction: validate, atomic replace, then adopt. No partial saveConfig(). */
public final class ArenaConfiguration {
    @FunctionalInterface interface Replace { void move(Path source, Path target) throws IOException; }
    private final Replace replace;
    private final Path file;
    private final DragonDefinitionRegistry definitions;
    private DevelopmentArena current;
    private String problem = "Arena unconfigured; use dev dragon setup.";
    public ArenaConfiguration(Path file, DragonDefinitionRegistry definitions) {
        this(file, definitions, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
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
    private YamlConfiguration read() throws IOException {
        var yaml = new YamlConfiguration();
        try { yaml.load(file.toFile()); }
        catch (org.bukkit.configuration.InvalidConfigurationException failure) { throw new IOException("Malformed config.yml", failure); }
        return yaml;
    }
    public Optional<DevelopmentArena> current() { return Optional.ofNullable(current); }
    public String problem() { return problem; }
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
