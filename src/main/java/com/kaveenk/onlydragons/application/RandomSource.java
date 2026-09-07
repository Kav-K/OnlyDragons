package com.kaveenk.onlydragons.application;

/** Injected randomness. Each draw must be finite and in [0, 1); consumers validate this boundary. */
@FunctionalInterface
public interface RandomSource {
    /**
     * Produces the next finite sample in [0,1). Consumers decide when a draw is required;
     * implementations may retain sequence state and need not be thread-safe. Inject deterministic
     * sequences for boundary tests rather than deriving new randomness inside damage consumers.
     * @return the next sample; callers reject malformed outputs
     */
    double nextDouble();
}
