package com.kaveenk.onlydragons.domain.stats;

/** Percentage points are stored as 25 for 25%, never as the probability 0.25. */
public enum StatUnit {
    /** Unscaled damage quantities before combat transformations. */ DAMAGE_POINTS,
        /** Percent units: 50 denotes fifty percent, not a fraction of 50. */ PERCENTAGE_POINTS,
        /** Proc chance units; each 100 points supplies one guaranteed child. */ FEROCITY_POINTS,
        /** Domain hit points, independent of native entity health. */ HEALTH_POINTS,
        /** Nonnegative defense units interpreted by the combat profile. */ DEFENSE_POINTS
}
