package com.kaveenk.onlydragons.paper.item.anvil;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantRecipes;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Strict PDC books. Ordinary names/lore/glint are never authority. */
public final class EnchantBookCodec {
    public static final NamespacedKey ROOT = key("enchant_book");
    private static final NamespacedKey SCHEMA = key("schema"), CATALOG = key("catalog"), ENCHANT = key("enchant"), LEVEL = key("level");
    public sealed interface Read {
        record Valid(EnchantBook book) implements Read {}
        record Invalid(String reason) implements Read {}
        record Ordinary() implements Read {}
    }
    private final EnchantRecipes recipes;

    public EnchantBookCodec(ItemRegistry registry) { recipes = new EnchantRecipes(registry); }

    public ItemStack encode(EnchantBook book) {
        requireThread();
        var stack = new ItemStack(Material.ENCHANTED_BOOK);
        return write(stack, book, false);
    }

    public ItemStack edit(ItemStack original, EnchantBook book) {
        requireThread();
        if (!(decode(original) instanceof Read.Valid before) || original.getAmount() != 1
                || !before.book().catalogRevision().equals(book.catalogRevision())
                || !before.book().enchantId().equals(book.enchantId()))
            throw new IllegalArgumentException("Invalid left book edit");
        return write(original.clone(), book, true);
    }

    private ItemStack write(ItemStack stack, EnchantBook book, boolean preserveName) {
        var enchant = recipes.validate(book);
        var meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        var data = pdc.getAdapterContext().newPersistentDataContainer();
        data.set(SCHEMA, PersistentDataType.INTEGER, 1);
        data.set(CATALOG, PersistentDataType.STRING, book.catalogRevision());
        data.set(ENCHANT, PersistentDataType.STRING, book.enchantId());
        data.set(LEVEL, PersistentDataType.INTEGER, book.level());
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, data);
        boolean ultimate = enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE;
        var color = ultimate ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.BLUE;
        if (!preserveName) meta.displayName(line("Enchanted Book", NamedTextColor.YELLOW));
        meta.lore(List.of(line(enchant.displayName() + " " + PresentationFormatter.roman(book.level()), color),
                line(ultimate ? "Ultimate Enchantment · One per bow" : "Bow Enchantment", color),
                line("Applies to managed bows and shortbows", NamedTextColor.GRAY),
                line("Anvil: " + EnchantRecipes.cost(enchant, book.level()) + " XP levels to apply", NamedTextColor.GRAY),
                line("Equal levels combine · Rename +1 level", NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        if (!(decode(stack) instanceof Read.Valid valid) || !valid.book().equals(book))
            throw new IllegalArgumentException("Book failed final validation");
        return stack;
    }

    public Read decode(ItemStack stack) {
        requireThread();
        if (stack == null || !stack.hasItemMeta()) return new Read.Ordinary();
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(ROOT)) return new Read.Ordinary();
        try {
            if (stack.getType() != Material.ENCHANTED_BOOK || stack.getAmount() < 1 || stack.getAmount() > stack.getMaxStackSize())
                throw new IllegalArgumentException("Invalid book material or amount");
            var data = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
            if (data == null || !data.getKeys().equals(Set.of(SCHEMA, CATALOG, ENCHANT, LEVEL))
                    || !Integer.valueOf(1).equals(data.get(SCHEMA, PersistentDataType.INTEGER)))
                throw new IllegalArgumentException("Invalid book schema");
            var level = data.get(LEVEL, PersistentDataType.INTEGER);
            if (level == null) throw new IllegalArgumentException("Missing book level");
            var book = new EnchantBook(data.get(CATALOG, PersistentDataType.STRING), data.get(ENCHANT, PersistentDataType.STRING), level);
            recipes.validate(book);
            return new Read.Valid(book);
        } catch (IllegalArgumentException invalid) {
            return new Read.Invalid(invalid.getMessage());
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    private static NamespacedKey key(String value) { return new NamespacedKey("onlydragons", value); }
    private static void requireThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Book access belongs to the server thread");
    }
}
