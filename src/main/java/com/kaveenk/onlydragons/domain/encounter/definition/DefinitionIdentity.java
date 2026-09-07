package com.kaveenk.onlydragons.domain.encounter.definition;

/**
 * Author-maintained reference labels, not a content hash. Retain the full resolved Selection as provenance.
 * @param id 1–64 token characters; first letter/digit/underscore/hyphen, then dot also allowed
 * @param schemaVersion exactly 1; no migration exists
 * @param revision same bounded token grammar, author-maintained and not automatically unique
 */
public record DefinitionIdentity(String id, int schemaVersion, String revision) {
    /**
     * Rejects null/malformed labels and unsupported schema before returning immutable identity.
     */
    public DefinitionIdentity {
        token(id);
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported definition schema: " + schemaVersion);
        token(revision);
    }

    /**
     * Validates and returns the original bounded ASCII token without trimming/case normalization;
     * invalid/null input throws IllegalArgumentException.
     */
    static String token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Missing or invalid definition ID/revision: " + value);
        }
        return value;
    }
}
