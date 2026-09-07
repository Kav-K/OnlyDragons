package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.DomainChecks;

/**
 * Integer percentage points avoid floating stack-boundary drift. Expiry is exclusive.
 * <p>
 * Immutable replacement value owned by the coordinator's current session; not an equipment stat.
 * @param bonusPercent shared bonus in 0–200 percentage points
 * @param expiresAt nonnegative exclusive game tick; construction does not consult a clock
 * @param sourceLevel latest qualifying I–V source, or 0 iff bonus is zero
 */
public record TempoState(int bonusPercent, long expiresAt, int sourceLevel) {
    /**
     * Compatibility constructor with source level zero; under current invariants only zero bonus is valid.
     * Use the canonical three-argument constructor or {@link #hit(int,long)} for active state.
     * @param bonusPercent shared bonus in 0\u2013200 percentage points
     * @param expiresAt nonnegative exclusive game tick; construction does not consult a clock
     */
    public TempoState(int bonusPercent, long expiresAt) {
        this(bonusPercent, expiresAt, 0);
    }

    /**
     * Rejects inconsistent bonus/source pairs, unsupported levels, out-of-range bonuses or negative expiry.
     * @param bonusPercent shared bonus in 0\u2013200 percentage points
     * @param expiresAt nonnegative exclusive game tick; construction does not consult a clock
     * @param sourceLevel latest qualifying I\u2013V source, or 0 iff bonus is zero
     */
    public TempoState {
        if (sourceLevel < 0 || sourceLevel > 5 || (bonusPercent == 0) != (sourceLevel == 0))
            throw new IllegalArgumentException("Tempo bonus/source mismatch");
        if (bonusPercent < 0 || bonusPercent > 200) throw new IllegalArgumentException("Tempo bonus outside 0–200");
        DomainChecks.nonNegative(expiresAt, "expiresAt");
    }

    /**
     * Returns the bonus strictly before expiry, otherwise zero, without changing this value.
     * @param tick nonnegative game tick; monotonic ordering is the coordinator's responsibility
     * @return live bonus percentage points
     */
    public int bonusAt(long tick) {
        DomainChecks.nonNegative(tick, "tick");
        return tick < expiresAt ? bonusPercent : 0;
    }

    /**
     * Adds level×10 to the still-live shared pool, caps at 200, sets expiry to tick+60, and replaces
     * source level even when the new level is lower. A hit exactly at old expiry starts a fresh pool.
     * @param level qualifying captured Fatal Tempo I–V
     * @param tick nonnegative game tick
     * @return new state; this value stays unchanged
     * @throws ArithmeticException if tick+60 overflows
     * @throws IllegalArgumentException for invalid tick or level
     */
    public TempoState hit(int level, long tick) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Fatal Tempo level must be 1–5");
        return new TempoState(Math.min(200, bonusAt(tick) + level * 10), Math.addExact(tick, 60), level);
    }
}
