package com.kaveenk.onlydragons.domain.combat;

/** Ordinary shot-time roll, separate from the captured mega-critical decision in
 * {@link com.kaveenk.onlydragons.domain.enchant.OverloadCapture}. */
public enum CritOutcome {
    /** Ordinary hit without the captured critical-damage multiplier. */ NORMAL,
        /** Ordinary critical hit; Overload remains an independent multiplier. */ CRITICAL
}
