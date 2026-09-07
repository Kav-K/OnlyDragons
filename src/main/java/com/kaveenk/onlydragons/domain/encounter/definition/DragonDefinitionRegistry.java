package com.kaveenk.onlydragons.domain.encounter.definition;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Serialized candidate adoption; readers retain immutable snapshots without locks or callbacks. */
public final class DragonDefinitionRegistry {
    private final DragonCatalogLoader loader;
    /**
     * Complete immutable catalog published after successful parsing; volatile visibility permits lock-free snapshot reads.
     */
    private volatile DragonCatalog current;

    /**
     * Loads bundled content through a nonnull loader before the registry becomes usable.
     * Bootstrap failures propagate; no partially initialized current catalog is published.
     */
    public DragonDefinitionRegistry(DragonCatalogLoader loader) {
        this.loader = Objects.requireNonNull(loader);
        current = loader.bundled();
    }

    /**
     * Returns the current immutable catalog through a volatile read; readers need no lock and
     * retained snapshots remain valid after later replacement.
     */
    public DragonCatalog snapshot() { return current; }

    /**
     * On any parse, validation or I/O failure the previous catalog remains active. Caller owns streams.
     * <p>
     * Serializes writers while parsing the complete candidate, then performs one volatile publication.
     * This method may block on I/O; callers own appropriate thread routing and stream lifetime.
     * @param definitions nonnull caller-owned dragon properties stream
     * @param tables nonnull caller-owned sample-table stream
     * @return newly published immutable catalog; labels may be reused without history indexing
     * @throws IOException if input reading fails, leaving the previous catalog active
     */
    public synchronized DragonCatalog replace(InputStream definitions, InputStream tables) throws IOException {
        var candidate = loader.load(definitions, tables);
        current = candidate;
        return candidate;
    }
}
