package com.kaveenk.onlydragons.domain;

import java.util.Objects;

/** Invariants shared by immutable domain contracts, independent of the server API. */
public final class DomainChecks {
    private DomainChecks() {}

    public static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    public static double nonNegative(double value, String name) {
        finite(value, name);
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    public static long nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }
}
