package com.kaveenk.onlydragons.domain.item.anvil;

import com.kaveenk.onlydragons.domain.item.EnchantDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.Objects;

/**
 * Compatibility boundary for future equipment content. No armor enchant is currently enabled.
 * <p>
 * Category and active slot are separate so future equipment must explicitly opt in.
 * @param category nonnull equipment family; ARMOR is currently unsupported
 * @param slot nonnull equipment location; only MAIN_HAND is supported now
 * @param firingMode nullable structural field; null cannot support any enchant
 */
public record EnchantTarget(Category category, Slot slot, WeaponDefinition.FiringMode firingMode) {
    /**
     * BOW is the only implemented family; ARMOR reserves a future explicit compatibility decision.
     */
    public enum Category {
        /** Enchant family for supported bow weapons. */ BOW,
        /** Enchant family for supported armor pieces. */ ARMOR
    }
    /**
     * Equipment locations for compatibility inspection; the current recipe admits only MAIN_HAND bows.
     */
    public enum Slot {
        /** Weapon contribution slot for the actively held main-hand item. */ MAIN_HAND,
        /** Off-hand slot, distinct from the active main-hand weapon. */ OFF_HAND,
        /** Helmet armor contribution slot. */ HEAD,
        /** Chestplate armor contribution slot. */ CHEST,
        /** Leggings armor contribution slot. */ LEGS,
        /** Boots armor contribution slot. */ FEET
    }

    /**
     * Requires category and slot but permits absent firing mode for an unsupported target.
     * @param category nonnull equipment family; ARMOR is currently unsupported
     * @param slot nonnull equipment location; only MAIN_HAND is supported now
     * @param firingMode nullable structural field; null cannot support any enchant
     */
    public EnchantTarget {
        Objects.requireNonNull(category);
        Objects.requireNonNull(slot);
    }

    /**
     * Returns true only for a main-hand bow with a nonnull compatible firing mode and available
     * descriptor. This is a capability predicate, not validation that a live item occupies that slot.
     * @param enchant trusted availability and firing-mode descriptor
     * @return true only for a compatible available enchant on the declared main-hand bow capability
     */
    public boolean supports(EnchantDefinition enchant) {
        return category == Category.BOW && slot == Slot.MAIN_HAND && firingMode != null
                && enchant.available() && enchant.compatibleModes().contains(firingMode);
    }
}
