package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trusted level table. Item input supplies an ID/level, never a kind or stat amount.
 * @param id lowercase item-key syntax, 1–64 characters
 * @param displayName nonblank generated label
 * @param kind nonnull trusted ordinary/ultimate category
 * @param compatibleModes nonempty copied supported firing modes
 * @param levelModifiers nonempty copied table with explicit levels 1–255 and immutable modifier lists; gaps are allowed
 * @param available whether consumers exist for selection in this catalog
 */
public record EnchantDefinition(String id, String displayName, WeaponDefinition.EnchantmentKind kind,
                                Set<WeaponDefinition.FiringMode> compatibleModes,
                                Map<Integer, List<StatModifier>> levelModifiers, boolean available) {
    /**
     * Convenience descriptor constructor selecting available=true; canonical table validation applies.
     */
    public EnchantDefinition(String id, String displayName, WeaponDefinition.EnchantmentKind kind,
                             Set<WeaponDefinition.FiringMode> compatibleModes,
                             Map<Integer, List<StatModifier>> levelModifiers) {
        this(id, displayName, kind, compatibleModes, levelModifiers, true);
    }

    /**
     * Returns the greatest explicitly defined table key; does not imply every lower level exists.
     */
    public int maxLevel() { return levelModifiers.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow(); }

    /**
     * Validates descriptor identity/modes/table and deep-copies the lists. Empty modifier lists are
     * valid for effects applied at firing/impact instead of stat resolution.
     */
    public EnchantDefinition {
        id = ItemValidationException.id(id);
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Missing enchant display name");
        Objects.requireNonNull(kind);
        compatibleModes = Set.copyOf(compatibleModes);
        if (compatibleModes.isEmpty()) throw new IllegalArgumentException("Enchant has no compatible firing mode");
        levelModifiers = levelModifiers.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        if (levelModifiers.isEmpty() || levelModifiers.keySet().stream().anyMatch(level -> level < 1 || level > 255)) {
            throw new IllegalArgumentException("Enchant levels must be explicitly defined in 1–255");
        }
    }

    /**
     * Returns a trusted selection only after availability, exact table level and firing-mode checks.
     * Failures use UNAVAILABLE_ENCHANT, INVALID_LEVEL or INCOMPATIBLE_ENCHANT in that order.
     * No modifiers are applied by this method.
     */
    public WeaponDefinition.Enchantment validate(int level, WeaponDefinition.FiringMode mode) {
        if (!available) throw new ItemValidationException(ItemValidationException.Code.UNAVAILABLE_ENCHANT,
                displayName + " is unavailable until its effect consumer is integrated");
        if (!levelModifiers.containsKey(level)) {
            throw new ItemValidationException(ItemValidationException.Code.INVALID_LEVEL, id + " does not support level " + level);
        }
        if (!compatibleModes.contains(mode)) {
            throw new ItemValidationException(ItemValidationException.Code.INCOMPATIBLE_ENCHANT, id + " is incompatible with " + mode);
        }
        return new WeaponDefinition.Enchantment(id, level, kind);
    }
}
