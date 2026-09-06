package com.kaveenk.onlydragons.domain.encounter.definition;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Serialized candidate adoption; readers retain immutable snapshots without locks or callbacks. */
public final class DragonDefinitionRegistry {
    private final DragonCatalogLoader loader;
    private volatile DragonCatalog current;

    public DragonDefinitionRegistry(DragonCatalogLoader loader) {
        this.loader = Objects.requireNonNull(loader);
        current = loader.bundled();
    }

    public DragonCatalog snapshot() { return current; }

    /** On any parse, validation or I/O failure the previous catalog remains active. Caller owns streams. */
    public synchronized DragonCatalog replace(InputStream definitions, InputStream tables) throws IOException {
        var candidate = loader.load(definitions, tables);
        current = candidate;
        return candidate;
    }
}
