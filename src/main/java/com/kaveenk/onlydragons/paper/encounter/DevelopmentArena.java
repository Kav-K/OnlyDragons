package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import org.bukkit.*;
import org.bukkit.util.BoundingBox;

/** Explicit bounded development cube; immutable configuration, never a location fallback. */
public record DevelopmentArena(String worldKey, double x, double y, double z, double radius, String type) {
    public DevelopmentArena {
        if (worldKey == null || !worldKey.matches("[a-z0-9._-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("World must be a namespaced key, e.g. minecraft:overworld.");
        if (type == null || !type.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid dragon type.");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(radius)
                || radius < 16 || radius > 48 || Math.abs(x) + radius > 29_999_984 || Math.abs(z) + radius > 29_999_984)
            throw new IllegalArgumentException("Finite coordinates and radius 16–48 within world limits required.");
    }
    public World world() {
        World world = Bukkit.getWorld(NamespacedKey.fromString(worldKey));
        if (world == null) throw new IllegalArgumentException("Arena world is missing or unloaded: " + worldKey);
        return world;
    }
    public DragonCatalog.Selection validate(DragonCatalog catalog) {
        World world = world();
        if (y - radius < world.getMinHeight() || y + radius >= world.getMaxHeight())
            throw new IllegalArgumentException("Arena bounds exceed world height.");
        for (double dx : new double[]{-radius, radius}) for (double dz : new double[]{-radius, radius})
            if (!world.getWorldBorder().isInside(new Location(world, x + dx, y, z + dz)))
                throw new IllegalArgumentException("Arena bounds exceed the world border.");
        return catalog.select(type);
    }
    public Location location() { return new Location(world(), x, y, z); }
    public BoundingBox bounds() { return BoundingBox.of(location(), radius, radius, radius); }
}
