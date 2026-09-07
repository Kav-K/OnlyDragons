package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;

/**
 * Immutable pre-hit shared-buff provenance, distinct from a child's Tempo refresh eligibility.
 * <p>
 * Bound to the physical parent by CombatEncounter and compared by full value at proc execution.
 * @param activeBonusPercent pre-hit shared bonus in 0–200 percentage points
 * @param activeSourceLevel 0 iff bonus is zero, otherwise latest source I–V
 * @param activeExpiresAt nonnegative exclusive expiry tick; zero iff bonus is zero
 * @param healthFraction finite HP fraction in [0,1] selected before the parent builds Tempo
 * @param resolvedCredit finite nonnegative capped parent basis, unmodified by remaining HP
 */
public record ProcHealthSnapshot(int activeBonusPercent, int activeSourceLevel, long activeExpiresAt,
                                 double healthFraction, double resolvedCredit) {
    /**
     * Validates consistent zero/active provenance and numeric bounds; the encounter verifies that
     * the supplied policy exactly matches its accepted parent, including credit.
     * @param activeBonusPercent pre-hit shared bonus in 0\u2013200 percentage points
     * @param activeSourceLevel 0 iff bonus is zero, otherwise latest source I\u2013V
     * @param activeExpiresAt nonnegative exclusive expiry tick; zero iff bonus is zero
     * @param healthFraction finite HP fraction in [0,1] selected before the parent builds Tempo
     * @param resolvedCredit finite nonnegative capped parent basis, unmodified by remaining HP
     */
    public ProcHealthSnapshot {
        if (activeBonusPercent < 0 || activeBonusPercent > 200 || activeSourceLevel < 0 || activeSourceLevel > 5
                || (activeBonusPercent == 0) != (activeSourceLevel == 0)
                || (activeBonusPercent == 0) != (activeExpiresAt == 0))
            throw new IllegalArgumentException("Invalid active Tempo provenance");
        DomainChecks.nonNegative(activeExpiresAt, "activeExpiresAt");
        DomainChecks.nonNegative(healthFraction, "healthFraction");
        if (healthFraction > 1) throw new IllegalArgumentException("HP fraction must be in [0, 1]");
        DomainChecks.nonNegative(resolvedCredit, "resolvedCredit");
    }
}
