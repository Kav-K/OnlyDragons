package com.kaveenk.onlydragons.application;

/** Injected game-tick clock. Implementations return nonnegative ticks within the active lifecycle. */
@FunctionalInterface
public interface TickClock {
    /**
     * Returns a nonnegative game tick from the active lifecycle, not wall-clock milliseconds.
     * Consumers that schedule work require nondecreasing values; lag does not shorten a tick-based
     * duration. The caller owns thread confinement and lifecycle reset of the implementation.
     * @return current game tick
     */
    long currentTick();
}
