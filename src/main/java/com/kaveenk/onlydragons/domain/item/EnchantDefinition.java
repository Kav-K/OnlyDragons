package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Trusted level table. Item input supplies an ID/level, never a kind or stat amount. */
public record EnchantDefinition(String id, String displayName, WeaponDefinition.EnchantmentKind kind,
                                Set<WeaponDefinition.FiringMode> compatibleModes,
                                Map<Integer, List<StatModifier>> levelModifiers) {
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

    public WeaponDefinition.Enchantment validate(int level, WeaponDefinition.FiringMode mode) {
        if (!levelModifiers.containsKey(level)) {
            throw new ItemValidationException(ItemValidationException.Code.INVALID_LEVEL, id + " does not support level " + level);
        }
        if (!compatibleModes.contains(mode)) {
            throw new ItemValidationException(ItemValidationException.Code.INCOMPATIBLE_ENCHANT, id + " is incompatible with " + mode);
        }
        return new WeaponDefinition.Enchantment(id, level, kind);
    }
}
