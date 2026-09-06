package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;

/** Immutable pre-hit shared-buff provenance, distinct from a child's Tempo refresh eligibility. */
public record ProcHealthSnapshot(int activeBonusPercent, int activeSourceLevel, long activeExpiresAt,
                                 double healthFraction, double resolvedCredit) {
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
