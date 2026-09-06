package com.kaveenk.onlydragons.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GreetingServiceTest {
    @Test
    void replacesEveryPlayerPlaceholder() {
        assertEquals("Hello Alex, Alex!", new GreetingService("Hello {player}, {player}!").welcome("Alex"));
    }

    @Test
    void acceptsLiteralAndEmptyMessages() {
        assertEquals("Hello!", new GreetingService("Hello!").welcome("Alex"));
        assertEquals("", new GreetingService("").welcome("Alex"));
    }

    @Test
    void rejectsMissingInputs() {
        assertThrows(NullPointerException.class, () -> new GreetingService(null));
        assertThrows(NullPointerException.class, () -> new GreetingService("hello").welcome(null));
    }
}
