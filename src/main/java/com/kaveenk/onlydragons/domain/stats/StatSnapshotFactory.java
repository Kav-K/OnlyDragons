package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.Map;
import java.util.stream.Collectors;

/** Domain-only weapon bridge; equipment adapters supply additional already-keyed contributions. */
public final class StatSnapshotFactory {
    private final StatResolver resolver;
    /**
     * Retains a resolver for the nonnull immutable profile; does not allocate snapshot revisions.
     * @param profile nonnull immutable stat policy used for all snapshots from this factory
     * @throws NullPointerException if profile is null
     */
    public StatSnapshotFactory(StatProfile profile) { resolver = new StatResolver(profile); }

    /**
     * Additional sources must be disjoint from weapon sources, preventing ambiguous double application.
     * <p>
     * Uses weapon base damage as the sole WEAPON_DAMAGE base override, then resolves weapon and external sources.
     * Pass {@link com.kaveenk.onlydragons.domain.item.ItemRegistry.ResolvedItem#resolvedWeapon()} once;
     * its enchant/roll modifiers are already included. A bare 100-damage bow retains calibration CD 50.
     * @param revision caller-allocated nonblank snapshot identity
     * @param weapon nonnull trusted resolved weapon
     * @param additionalSources nonnull external gear/buff sources, excluding the weapon's own sources
     * @return immutable complete arithmetic result
     * @throws IllegalArgumentException for source collisions or invalid resolution
     */
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
