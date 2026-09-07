package com.kaveenk.onlydragons.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Pure string-contract tests: exact expected messages exercise replacement and null rejection without a Bukkit server.
 */
class GreetingServiceTest {
    /**
     * Uses repeated literal placeholders to prove replacement is global.
     */
    @Test
    void replacesEveryPlayerPlaceholder() {
        assertEquals("Hello Alex, Alex!", new GreetingService("Hello {player}, {player}!").welcome("Alex"));
    }

    /**
     * Preserves caller-supplied literal and empty templates exactly.
     */
    @Test
    void acceptsLiteralAndEmptyMessages() {
        assertEquals("Hello!", new GreetingService("Hello!").welcome("Alex"));
        assertEquals("", new GreetingService("").welcome("Alex"));
    }

    /**
     * Asserts explicit null rejection at construction and message generation.
     */
    @Test
    void rejectsMissingInputs() {
        assertThrows(NullPointerException.class, () -> new GreetingService(null));
        assertThrows(NullPointerException.class, () -> new GreetingService("hello").welcome(null));
    }
}
