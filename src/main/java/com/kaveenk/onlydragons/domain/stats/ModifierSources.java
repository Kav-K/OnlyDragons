package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Persistent value collection; refresh returns a new value and never changes captured sources. */
public final class ModifierSources {
    private final Map<String, List<StatModifier>> sources;

    private ModifierSources(Map<String, List<StatModifier>> sources) { this.sources = Map.copyOf(sources); }
    public static ModifierSources empty() { return new ModifierSources(Map.of()); }
    public Map<String, List<StatModifier>> sources() { return sources; }

    /** Empty contributions remove the source. All entries must carry the supplied source ID. */
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

    public List<StatModifier> modifiers() {
        return sources.values().stream().flatMap(List::stream).sorted(StatModifier.EXPLANATION_ORDER).toList();
    }
}
