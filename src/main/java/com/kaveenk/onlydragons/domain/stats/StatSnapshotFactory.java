package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.Map;
import java.util.stream.Collectors;

/** Domain-only weapon bridge; equipment adapters supply additional already-keyed contributions. */
public final class StatSnapshotFactory {
    private final StatResolver resolver;
    public StatSnapshotFactory(StatProfile profile) { resolver = new StatResolver(profile); }

    /** Additional sources must be disjoint from weapon sources, preventing ambiguous double application. */
    public ExplainedStatSnapshot create(String revision, WeaponDefinition weapon, ModifierSources additionalSources) {
        var sources = additionalSources;
        var weaponSources = weapon.statModifiers().stream().collect(Collectors.groupingBy(StatModifier::sourceId));
        for (var entry : weaponSources.entrySet()) {
            if (additionalSources.sources().containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Additional source collides with weapon source: " + entry.getKey());
            }
            sources = sources.replace(entry.getKey(), entry.getValue());
        }
        return resolver.resolve(revision, Map.of(StatKey.WEAPON_DAMAGE, weapon.baseDamage()), sources);
    }
}
