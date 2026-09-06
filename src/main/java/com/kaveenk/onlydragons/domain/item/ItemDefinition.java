package com.kaveenk.onlydragons.domain.item;

import java.util.Objects;
import java.util.Set;

/** Presentation and allowed roll IDs around the stable T00 offensive definition. */
public record ItemDefinition(WeaponDefinition weapon, String displayName, String material, Set<String> allowedRolls) {
    public ItemDefinition {
        Objects.requireNonNull(weapon);
        ItemValidationException.id(weapon.id());
        if (!weapon.revision().matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid definition revision");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Missing item display name");
        // This bounded catalog supports bows. New material families require a deliberate compatibility rule.
        if (!"BOW".equals(material)) throw new IllegalArgumentException("Only BOW material is supported");
        allowedRolls = Set.copyOf(allowedRolls);
        allowedRolls.forEach(ItemValidationException::id);
    }
}
