package com.kaveenk.onlydragons.domain.stats;

/** Percentage points are stored as 25 for 25%, never as the probability 0.25. */
public enum StatUnit {
    DAMAGE_POINTS, PERCENTAGE_POINTS, FEROCITY_POINTS, HEALTH_POINTS, DEFENSE_POINTS
}
