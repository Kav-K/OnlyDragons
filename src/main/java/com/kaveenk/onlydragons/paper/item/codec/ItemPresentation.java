package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.meta.ItemMeta;

/** Generated text is an output only. The codec never reads it as gameplay data. */
public final class ItemPresentation {
    private ItemPresentation() {}

    static void apply(ItemMeta meta, ItemRegistry.ResolvedItem item, ItemRegistry registry) {
        meta.displayName(line(item.definition().displayName(), NamedTextColor.AQUA));
        var lore = new ArrayList<Component>();
        lore.add(line("Weapon damage: " + item.definition().weapon().baseDamage(), NamedTextColor.GRAY));
        for (var modifier : item.statModifiers()) {
            lore.add(line(modifier.key().id() + ": " + modifier.amount() + " (" + modifier.operation() + ")", NamedTextColor.GRAY));
        }
        for (var enchant : item.enchantments()) {
            lore.add(line(registry.enchant(enchant.id()).displayName() + " " + enchant.level(),
                    enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.BLUE));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(!item.enchantments().isEmpty());
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
