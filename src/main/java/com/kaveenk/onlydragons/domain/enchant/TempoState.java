package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.DomainChecks;

/** Integer percentage points avoid floating stack-boundary drift. Expiry is exclusive. */
public record TempoState(int bonusPercent, long expiresAt, int sourceLevel) {
    public TempoState(int bonusPercent, long expiresAt) {
        this(bonusPercent, expiresAt, 0);
    }

    public TempoState {
        if (sourceLevel < 0 || sourceLevel > 5 || (bonusPercent == 0) != (sourceLevel == 0))
            throw new IllegalArgumentException("Tempo bonus/source mismatch");
        if (bonusPercent < 0 || bonusPercent > 200) throw new IllegalArgumentException("Tempo bonus outside 0–200");
        DomainChecks.nonNegative(expiresAt, "expiresAt");
    }

    public int bonusAt(long tick) {
        DomainChecks.nonNegative(tick, "tick");
        return tick < expiresAt ? bonusPercent : 0;
    }

    public TempoState hit(int level, long tick) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Fatal Tempo level must be 1–5");
        return new TempoState(Math.min(200, bonusAt(tick) + level * 10), Math.addExact(tick, 60), level);
    }
}
