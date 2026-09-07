package com.kaveenk.onlydragons.service;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Immutable, server-independent replacement of the literal player placeholder.
 * This plain Java formatter is safe to share across threads; it performs no MiniMessage
 * parsing, permissions, I/O or broadcasts. Bukkit callers decide how to deliver text.
 */
public final class GreetingService {
    private final String template;

    /**
     * Captures the exact template; all occurrences of the literal placeholder are replaced.
     * @param template non-null welcome text, optionally containing {@code {player}}
     * @throws NullPointerException if template is null
     */
    public GreetingService(String template) {
        this.template = Objects.requireNonNull(template);
    }

    /**
     * Produces welcome text without modifying or interpreting the configured template.
     * @param playerName non-null replacement, treated literally even if it contains markup
     * @return non-null template with every player placeholder replaced
     * @throws NullPointerException if playerName is null
     */
    public @NonNull String welcome(String playerName) {
        return Objects.requireNonNull(template.replace("{player}", Objects.requireNonNull(playerName)));
    }
}
