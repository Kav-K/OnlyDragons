package com.kaveenk.onlydragons.domain.item;

/** Stable rejection reasons for grants, edits, and persistent item reads. */
public final class ItemValidationException extends IllegalArgumentException {
    /**
     * Machine-readable reasons shared by registry and item codecs. WRONG_MATERIAL/INVALID_AMOUNT
     * are adapter checks; schema/revision errors require explicit migration rather than silent fallback.
     */
    public enum Code {
        /** Encoded data or stable identifier syntax is invalid. */ MALFORMED_DATA,
        /** The encoded item schema has no supported reader. */ UNSUPPORTED_SCHEMA,
        /** The definition and supplied revision do not match. */ REVISION_MISMATCH,
        /** No definition exists for the requested identity. */ UNKNOWN_DEFINITION,
        /** The enchant ID is absent from the selected catalog. */ UNKNOWN_ENCHANT,
        /** The catalog knows the enchant but does not permit its use. */ UNAVAILABLE_ENCHANT,
        /** The requested level has no permitted table entry. */ INVALID_LEVEL,
        /** The enchant cannot apply to this item target. */ INCOMPATIBLE_ENCHANT,
        /** More than one ultimate enchant is present. */ MULTIPLE_ULTIMATES,
        /** The roll ID is absent from the catalog. */ UNKNOWN_ROLL,
        /** The item repeats a roll that must be unique. */ DUPLICATE_ROLL,
        /** The physical item material disagrees with its definition. */ WRONG_MATERIAL,
        /** The physical item count violates the codec’s item rule. */ INVALID_AMOUNT
    }
    private final Code code;

    /**
     * Creates a rejection with a nonnull stable code and diagnostic message (which may be null).
     * @param code nonnull stable rejection category
     * @param message diagnostic message, possibly null
     * @throws NullPointerException if code is null
     */
    public ItemValidationException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    /**
     * Returns the stable rejection category; callers should not parse the human-readable message.
     * @return stable rejection category for programmatic handling without message parsing
     */
    public Code code() { return code; }

    /**
     * Returns unchanged text only if it matches [a-z0-9_]{1,64}; null/invalid syntax throws
     * MALFORMED_DATA. Does not trim, lowercase or silently repair identity.
     */
    static String id(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,64}")) {
            throw new ItemValidationException(Code.MALFORMED_DATA, "Expected a lowercase item key of 1–64 characters");
        }
        return value;
    }
}
