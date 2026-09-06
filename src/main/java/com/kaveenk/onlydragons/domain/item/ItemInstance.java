package com.kaveenk.onlydragons.domain.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Untrusted immutable input until ItemRegistry.resolve validates it against a trusted revision. */
public record ItemInstance(WeaponIdentity identity, String registryRevision,
                           Map<String, Integer> enchantLevels, List<String> rolledModifierIds) {
    public ItemInstance {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(registryRevision);
        enchantLevels = Map.copyOf(enchantLevels);
        rolledModifierIds = List.copyOf(rolledModifierIds).stream().sorted().toList();
    }
}
