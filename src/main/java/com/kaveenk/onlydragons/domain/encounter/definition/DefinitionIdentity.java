package com.kaveenk.onlydragons.domain.encounter.definition;

/** Author-maintained reference labels, not a content hash. Retain the full resolved Selection as provenance. */
public record DefinitionIdentity(String id, int schemaVersion, String revision) {
    public DefinitionIdentity {
        token(id);
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported definition schema: " + schemaVersion);
        token(revision);
    }

    static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Missing or invalid definition ID/revision: " + value);
        }
        return value;
    }
}
