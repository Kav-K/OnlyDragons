package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.application.PresentationFormatter;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Generates weapon text and glint from trusted catalog values, never the reverse.
 * Meta is caller-owned; callers apply it back to a stack on the server thread.
 * {@link WeaponItemCodec#edit} uses lore-only refresh to preserve user/foreign names.
 */
public final class ItemPresentation {
    /**
     * Prevents instances of this stateless presentation utility.
     */
    private ItemPresentation() {}

    /**
     * Initializes a newly encoded stack's catalog name, lore and glint.
     * @param meta mutable metadata of the new stack
     * @param item fully resolved trusted weapon
     * @param registry router containing the weapon's exact catalog revision
     */
    static void apply(ItemMeta meta, ItemRegistry.ResolvedItem item, ItemRegistry registry) {
        meta.displayName(line(item.definition().displayName(), NamedTextColor.AQUA));
        refreshLore(meta, item, registry);
    }

    /**
     * Replaces generated lore/glint while leaving custom names and unrelated components alone.
     * Modifier units and enchant names come from the resolved values and exact catalog.
     * @param meta caller-owned mutable item metadata, changed in place
     * @param item complete validated instance and its resolved sources
     * @param registry catalog router containing the instance's revision
     * @throws IllegalArgumentException if the required catalog/enchant cannot be resolved
     */
    public static void refreshLore(ItemMeta meta, ItemRegistry.ResolvedItem item, ItemRegistry registry) {
        var lore = new ArrayList<Component>();
        lore.add(line(item.definition().weapon().firingMode() == WeaponDefinition.FiringMode.SHORTBOW
                ? "Instant bow · hold right-click" : "Draw and release", NamedTextColor.GRAY));
        lore.add(line("Weapon Damage: " + PresentationFormatter.number(item.definition().weapon().baseDamage()), NamedTextColor.GRAY));
        for (var modifier : item.statModifiers()) {
            lore.add(line(PresentationFormatter.statModifier(modifier), NamedTextColor.GRAY));
        }
        for (var enchant : item.enchantments()) {
            lore.add(line(registry.catalog(item.instance().registryRevision()).enchant(enchant.id()).displayName() + " " + PresentationFormatter.roman(enchant.level()),
                    enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.BLUE));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(!item.enchantments().isEmpty());
    }

    /**
     * Builds a literal colored line with italics explicitly disabled.
     * @param text already formatted trusted display text
     * @param color visual category
     * @return new non-italic component; no markup is parsed
     */
    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
