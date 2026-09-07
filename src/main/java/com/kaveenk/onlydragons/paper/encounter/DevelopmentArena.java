package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog;
import org.bukkit.*;
import org.bukkit.util.BoundingBox;

/**
 * Explicit immutable development cube, never inferred from a player's location.
 * Construction checks value syntax; {@link #validate} additionally checks the live
 * loaded world/border and trusted type. World-facing methods require the server thread.
 * @param worldKey namespaced loaded-world key when resolved
 * @param x finite center X in blocks
 * @param y finite center Y in blocks
 * @param z finite center Z in blocks
 * @param radius cube half-extent in blocks, inclusive range16–48
 * @param type lowercase trusted dragon-type identifier
 */
public record DevelopmentArena(String worldKey, double x, double y, double z, double radius, String type) {
    /**
     * Validates finite coordinates, radius, world-key/type syntax and hard X/Z limits.
     * This constructor does not resolve or load a world; validate performs that check later.
     * @param worldKey namespaced world key, not a filesystem path
     * @param x finite centre X in blocks, with the full cube inside the hard world limit
     * @param y finite centre Y in blocks; live world height is checked by validate
     * @param z finite centre Z in blocks, with the full cube inside the hard world limit
     * @param radius cube half-extent in blocks, inclusive 16–48
     * @param type lowercase dragon-type ID, resolved against the catalog later
     * @throws IllegalArgumentException if syntax or coordinate bounds are invalid
     */
    public DevelopmentArena {
        if (worldKey == null || !worldKey.matches("[a-z0-9._-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("World must be a namespaced key, e.g. minecraft:overworld.");
        if (type == null || !type.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid dragon type.");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(radius)
                || radius < 16 || radius > 48 || Math.abs(x) + radius > 29_999_984 || Math.abs(z) + radius > 29_999_984)
            throw new IllegalArgumentException("Finite coordinates and radius 16–48 within world limits required.");
    }
    /**
     * Resolves the currently loaded world without loading or creating one.
     * @return non-null matching native world
     * @throws IllegalArgumentException if the world is missing or unloaded
     */
    public World world() {
        World world = Bukkit.getWorld(NamespacedKey.fromString(worldKey));
        if (world == null) throw new IllegalArgumentException("Arena world is missing or unloaded: " + worldKey);
        return world;
    }
    /**
     * Checks whole-cube world height/border containment and resolves the trusted type.
     * @param catalog immutable catalog whose complete selection will be retained
     * @return full selected type/profile/table provenance
     * @throws IllegalArgumentException if world, bounds or type selection is invalid
     */
    public DragonCatalog.Selection validate(DragonCatalog catalog) {
        World world = world();
        if (y - radius < world.getMinHeight() || y + radius >= world.getMaxHeight())
            throw new IllegalArgumentException("Arena bounds exceed world height.");
        for (double dx : new double[]{-radius, radius}) for (double dz : new double[]{-radius, radius})
            if (!world.getWorldBorder().isInside(new Location(world, x + dx, y, z + dz)))
                throw new IllegalArgumentException("Arena bounds exceed the world border.");
        return catalog.select(type);
    }
    /**
     * Creates a detached center location in the currently loaded world.
     * @return new mutable Location in block units
     * @throws IllegalArgumentException if the configured world is unavailable
     */
    public Location location() { return new Location(world(), x, y, z); }
    /**
     * Creates the full axis-aligned cube used for projectile/target admission.
     * @return new mutable bounding box; modifying it does not edit this configuration
     * @throws IllegalArgumentException if the configured world is unavailable
     */
    public BoundingBox bounds() { return BoundingBox.of(location(), radius, radius, radius); }
}
