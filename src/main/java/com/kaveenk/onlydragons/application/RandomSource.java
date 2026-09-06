package com.kaveenk.onlydragons.application;

/** Injected randomness. Each draw must be finite and in [0, 1); consumers validate this boundary. */
@FunctionalInterface
public interface RandomSource {
    double nextDouble();
}
