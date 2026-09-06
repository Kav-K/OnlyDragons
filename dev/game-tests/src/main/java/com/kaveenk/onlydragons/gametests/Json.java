package com.kaveenk.onlydragons.gametests;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Small report serializer, avoiding a runtime dependency or server-internal JSON library. */
final class Json {
    private Json() { }
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
