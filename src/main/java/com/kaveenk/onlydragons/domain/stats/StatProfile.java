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

/**
 * Immutable validated calibration. Construct a candidate fully before adopting it.
 * <p>
 * The full value is retained by {@link StatResolver}; replacement does not update existing resolvers.
 * @param id nonblank policy family
 * @param version positive author-maintained version
 * @param definitions complete immutable copy keyed by each definition's own key
 * @param effectiveCaps complete immutable copy of finite caps inside each definition's raw range
 */
public record StatProfile(String id, int version, Map<StatKey, StatDefinition> definitions,
                          Map<StatKey, Double> effectiveCaps) {
    /**
     * Validates all definitions and caps before returning a candidate; missing/null entries reject.
     * Labels may be reused by callers, so {@link #revision()} is not a content hash.
     */
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

    /**
     * Returns the display/provenance label id + "-v" + version, without registering or adopting it.
     */
    public String revision() { return id + "-v" + version; }

    /**
     * Loads and closes the bundled calibration resource. Missing resource or I/O failure throws
     * IllegalStateException; malformed policy content retains its validation exception.
     */
    public static StatProfile calibration() {
        try (var input = StatProfile.class.getResourceAsStream("/stats/calibration-v1.properties")) {
            if (input == null) throw new IllegalStateException("Missing stats calibration resource");
            return load(input);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read stats calibration", failure); }
    }

    /**
     * Strict property vocabulary; caller owns the stream. No partial/defaulted candidate is returned.
     * <p>
     * Duplicate, missing, unknown and unsupported-policy properties reject with IllegalArgumentException.
     * @param input nonnull Java-properties stream, left open for its caller
     * @return fully validated immutable profile
     * @throws IOException if the stream cannot be read
     */
    public static StatProfile load(InputStream input) throws IOException {
        var properties = new Properties() {
            /**
             * Rejects a repeated property before replacement, preserving evidence that Properties.load would otherwise overwrite.
             * Returns the superclass insertion result for a new key; this parser is not a general concurrent store.
             */
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

    /**
     * Reads one mandatory numeric field; missing and malformed values reject before profile adoption.
     * Finite/range validation belongs to the constructed definition and profile.
     */
    private static double number(Properties properties, StatKey key, String field) {
        String value = properties.getProperty(key.id() + "." + field);
        if (value == null) throw new IllegalArgumentException("Missing " + key.id() + "." + field);
        return Double.parseDouble(value);
    }
}
