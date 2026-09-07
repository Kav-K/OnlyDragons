package com.kaveenk.onlydragons.domain;

import java.util.Objects;

/** Invariants shared by immutable domain contracts, independent of the server API. */
public final class DomainChecks {
    private DomainChecks() {}

    /**
     * Returns the original nonblank text without trimming or normalizing identity.
     * @param value text to validate; null throws {@link NullPointerException}
     * @param name diagnostic field label
     * @return the identical string
     * @throws IllegalArgumentException if all characters are whitespace or the value is empty
     */
    public static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    /**
     * Rejects NaN and either infinity; signed zero and negative finite values are allowed.
     * @param value number in the caller's units
     * @param name diagnostic field label
     * @return the unchanged value
     * @throws IllegalArgumentException if the value is not finite
     */
    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    /**
     * Validates a nonnegative finite number (including signed zero), without clamping.
     * @param value number in the caller's units
     * @param name diagnostic field label
     * @return the unchanged value
     * @throws IllegalArgumentException if non-finite or negative
     */
    public static double nonNegative(double value, String name) {
        finite(value, name);
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    /**
     * Validates a nonnegative integer, without clamping.
     * @param value number in the caller's units
     * @param name diagnostic field label
     * @return the unchanged value
     * @throws IllegalArgumentException if negative
     */
    public static long nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }
}
