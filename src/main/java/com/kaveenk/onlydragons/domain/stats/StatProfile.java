package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Immutable validated calibration. Construct a candidate fully before adopting it. */
public record StatProfile(String id, int version, Map<StatKey, StatDefinition> definitions,
                          Map<StatKey, Double> effectiveCaps) {
    public StatProfile {
        DomainChecks.text(id, "profile id");
        if (version < 1) throw new IllegalArgumentException("Profile version must be positive");
        definitions = Map.copyOf(definitions);
        effectiveCaps = Map.copyOf(effectiveCaps);
        for (StatKey key : StatKey.values()) {
            var definition = Objects.requireNonNull(definitions.get(key), "Missing definition " + key);
            if (definition.key() != key) throw new IllegalArgumentException("Definition key mismatch");
            double cap = Objects.requireNonNull(effectiveCaps.get(key), "Missing cap " + key);
            definition.validateRaw(cap);
        }
    }

    public String revision() { return id + "-v" + version; }

    public static StatProfile calibration() {
        try (var input = StatProfile.class.getResourceAsStream("/stats/calibration-v1.properties")) {
            if (input == null) throw new IllegalStateException("Missing stats calibration resource");
            return load(input);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read stats calibration", failure); }
    }

    /** Strict property vocabulary; caller owns the stream. No partial/defaulted candidate is returned. */
    public static StatProfile load(InputStream input) throws IOException {
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate profile property: " + key);
                return super.put(key, value);
            }
        };
        properties.load(Objects.requireNonNull(input, "input"));
        Set<String> expected = new HashSet<>(Set.of("id", "version", "negativeResults", "multiplierTies"));
        var definitions = new EnumMap<StatKey, StatDefinition>(StatKey.class);
        var caps = new EnumMap<StatKey, Double>(StatKey.class);
        for (StatKey key : StatKey.values()) {
            for (String field : new String[]{"default", "minimum", "maximum", "cap"}) expected.add(key.id() + "." + field);
            definitions.put(key, new StatDefinition(key, number(properties, key, "default"),
                    number(properties, key, "minimum"), number(properties, key, "maximum")));
            caps.put(key, number(properties, key, "cap"));
        }
        if (!properties.stringPropertyNames().equals(expected)) throw new IllegalArgumentException("Unexpected or missing profile properties");
        if (!"reject".equals(properties.getProperty("negativeResults"))
                || !"order-source-amount".equals(properties.getProperty("multiplierTies"))) {
            throw new IllegalArgumentException("Unsupported resolution policy");
        }
        return new StatProfile(properties.getProperty("id"), Integer.parseInt(properties.getProperty("version")), definitions, caps);
    }

    private static double number(Properties properties, StatKey key, String field) {
        String value = properties.getProperty(key.id() + "." + field);
        if (value == null) throw new IllegalArgumentException("Missing " + key.id() + "." + field);
        return Double.parseDouble(value);
    }
}
