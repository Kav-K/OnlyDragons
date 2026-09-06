package com.kaveenk.onlydragons.application;

/** Injected game-tick clock. Implementations return nonnegative ticks within the active lifecycle. */
@FunctionalInterface
public interface TickClock {
    long currentTick();
}
