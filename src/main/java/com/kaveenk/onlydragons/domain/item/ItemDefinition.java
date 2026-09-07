package com.kaveenk.onlydragons.domain.item;

import java.util.Objects;
import java.util.Set;

/**
 * Presentation and allowed roll IDs around the stable T00 offensive definition.
 * @param weapon nonnull offensive definition with strict item ID and bounded revision syntax
 * @param displayName nonblank generated presentation, never decoded as authority
 * @param material exactly BOW in this bounded catalog
 * @param allowedRolls copied set of valid named roll IDs; registry verifies they exist
 */
public record ItemDefinition(WeaponDefinition weapon, String displayName, String material, Set<String> allowedRolls) {
    /**
     * Rejects invalid presentation/reference syntax or unsupported material; freezes allowed roll IDs.
     * Adding another material family requires an explicit compatibility contract.
     * @param weapon nonnull offensive definition with strict item ID and bounded revision syntax
     * @param displayName nonblank generated presentation, never decoded as authority
     * @param material exactly BOW in this bounded catalog
     * @param allowedRolls copied set of valid named roll IDs; registry verifies they exist
     */
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
