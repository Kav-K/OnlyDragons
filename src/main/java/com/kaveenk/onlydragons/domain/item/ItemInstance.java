package com.kaveenk.onlydragons.domain.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Untrusted immutable input until ItemRegistry.resolve validates it against a trusted revision.
 * <p>
 * Defaults are not reapplied on loading; selections here are the complete requested state.
 * @param identity nonnull persistent identity
 * @param registryRevision nonnull exact catalog label; compatibility is deferred to resolve
 * @param enchantLevels copied map of requested ID/level pairs, not trusted effect descriptors
 * @param rolledModifierIds copied sorted named selections; duplicates remain until resolve rejects them
 */
public record ItemInstance(WeaponIdentity identity, String registryRevision,
                           Map<String, Integer> enchantLevels, List<String> rolledModifierIds) {
    /**
     * Copies caller collections and sorts rolls without interpreting selections; malformed or forged
     * content may still be structurally constructible and must pass ItemRegistry.resolve.
     */
    public ItemInstance {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(registryRevision);
        enchantLevels = Map.copyOf(enchantLevels);
        rolledModifierIds = List.copyOf(rolledModifierIds).stream().sorted().toList();
    }
}
