package com.kaveenk.onlydragons.domain.item;

/** Stable rejection reasons for grants, edits, and persistent item reads. */
public final class ItemValidationException extends IllegalArgumentException {
    public enum Code {
        MALFORMED_DATA, UNSUPPORTED_SCHEMA, REVISION_MISMATCH, UNKNOWN_DEFINITION,
        UNKNOWN_ENCHANT, UNAVAILABLE_ENCHANT, INVALID_LEVEL, INCOMPATIBLE_ENCHANT,
        MULTIPLE_ULTIMATES, UNKNOWN_ROLL, DUPLICATE_ROLL, WRONG_MATERIAL, INVALID_AMOUNT
    }
    private final Code code;

    public ItemValidationException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }

    static String id(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,64}")) {
            throw new ItemValidationException(Code.MALFORMED_DATA, "Expected a lowercase item key of 1–64 characters");
        }
        return value;
    }
}
