package com.kaveenk.onlydragons.domain.stats;

/** Applied after base values: flat additions, summed percentage points, then ordered factors. */
public enum ModifierOperation {
    /** Adds amounts in the stat’s own unit before percentage and multiplier layers. */ FLAT,
        /** Sums percentage points into one factor; 50 means multiplication by 1.5. */ ADDITIVE_PERCENT,
        /** Applies an ordered nonnegative factor; 0.5 halves the preceding value. */ MULTIPLIER
}
