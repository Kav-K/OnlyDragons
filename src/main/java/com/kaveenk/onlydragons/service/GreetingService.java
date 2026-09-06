package com.kaveenk.onlydragons.service;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Plain Java logic stays separate from server APIs so it is easy to test and
 * migrate.
 */
public final class GreetingService {
    private final String template;

    public GreetingService(String template) {
        this.template = Objects.requireNonNull(template);
    }

    public @NonNull String welcome(String playerName) {
        return Objects.requireNonNull(template.replace("{player}", Objects.requireNonNull(playerName)));
    }
}
