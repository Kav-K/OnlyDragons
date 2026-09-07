package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Persistent value collection; refresh returns a new value and never changes captured sources. */
public final class ModifierSources {
    /**
     * Frozen source-ID map of copied modifier lists; replacement returns a new value instead of mutating retained snapshots.
     */
    private final Map<String, List<StatModifier>> sources;

    private ModifierSources(Map<String, List<StatModifier>> sources) { this.sources = Map.copyOf(sources); }
    /**
     * Returns a fresh empty immutable source collection; no global mutable registry is shared.
     */
    public static ModifierSources empty() { return new ModifierSources(Map.of()); }
    /**
     * Returns the immutable source map and immutable lists; map iteration order is not a contract.
     */
    public Map<String, List<StatModifier>> sources() { return sources; }

    /**
     * Empty contributions remove the source. All entries must carry the supplied source ID.
     * <p>
     * The current collection is untouched on success or failure; duplicate modifiers are retained.
     * @param sourceId nonblank exact source identity
     * @param contributions nonnull collection without null entries; copied and canonically sorted
     * @return a new collection with the whole source replaced
     * @throws IllegalArgumentException if any contribution has another source ID
     */
    public ModifierSources replace(String sourceId, Collection<StatModifier> contributions) {
        DomainChecks.text(sourceId, "sourceId");
        var copy = List.copyOf(contributions);
        if (copy.stream().anyMatch(modifier -> !sourceId.equals(modifier.sourceId()))) {
            throw new IllegalArgumentException("Contribution source mismatch: " + sourceId);
        }
        var updated = new TreeMap<>(sources);
        if (copy.isEmpty()) updated.remove(sourceId);
        else updated.put(sourceId, copy.stream().sorted(StatModifier.EXPLANATION_ORDER).toList());
        return new ModifierSources(updated);
    }

    /**
     * Returns a new immutable flattened list in {@link StatModifier#EXPLANATION_ORDER},
     * including repeated contributions; callers must not apply the map and this list twice.
     */
    public List<StatModifier> modifiers() {
        return sources.values().stream().flatMap(List::stream).sorted(StatModifier.EXPLANATION_ORDER).toList();
    }
}
