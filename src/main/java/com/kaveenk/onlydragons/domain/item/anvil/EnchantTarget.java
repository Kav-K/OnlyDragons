package com.kaveenk.onlydragons.domain.item.anvil;

import com.kaveenk.onlydragons.domain.item.EnchantDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.Objects;

/** Compatibility boundary for future equipment content. No armor enchant is currently enabled. */
public record EnchantTarget(Category category, Slot slot, WeaponDefinition.FiringMode firingMode) {
    public enum Category { BOW, ARMOR }
    public enum Slot { MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET }

    public EnchantTarget {
        Objects.requireNonNull(category);
        Objects.requireNonNull(slot);
    }

    public boolean supports(EnchantDefinition enchant) {
        return category == Category.BOW && slot == Slot.MAIN_HAND && firingMode != null
                && enchant.available() && enchant.compatibleModes().contains(firingMode);
    }
}
