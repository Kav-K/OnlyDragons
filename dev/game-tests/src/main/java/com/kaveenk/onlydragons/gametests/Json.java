package com.kaveenk.onlydragons.gametests;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Minimal serializer for explicit report primitives, maps and iterables.
 * It deliberately rejects arbitrary Java/Bukkit objects and non-finite numbers so
 * an unsupported observation cannot silently become misleading string evidence.
 */
final class Json {
    /**
     * Prevents instances of the stateless report serializer.
     */
    private Json() { }
    /**
     * Serializes recursively, escaping strings and requiring finite numeric values.
     * Map keys are represented by their string form; callers provide stable unique keys.
     * @param value JSON-shaped report value, including {@code null}
     * @return JSON text ready to freeze before handing it to the report writer
     * @throws IllegalArgumentException for non-finite numbers or unsupported value types
     */
    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Non-finite report number");
            return number.toString();
        }
        if (value instanceof String text) {
            StringBuilder quoted = new StringBuilder("\"");
            for (char character : text.toCharArray()) {
                switch (character) {
                    case '"' -> quoted.append("\\\"");
                    case '\\' -> quoted.append("\\\\");
                    case '\n' -> quoted.append("\\n");
                    case '\r' -> quoted.append("\\r");
                    case '\t' -> quoted.append("\\t");
                    default -> { if (character < 32) quoted.append(String.format("\\u%04x", (int) character)); else quoted.append(character); }
                }
            }
            return quoted.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(entry -> write(entry.getKey().toString()) + ":" + write(entry.getValue())).collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> list) return StreamSupport.stream(list.spliterator(), false).map(Json::write).collect(Collectors.joining(",", "[", "]"));
        throw new IllegalArgumentException("Unsupported report value: " + value.getClass());
    }
}
