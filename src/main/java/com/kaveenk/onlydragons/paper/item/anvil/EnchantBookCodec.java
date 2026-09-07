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

/**
 * Strict schema-v1 custom-book codec, separate from managed weapon metadata.
 * PDC and trusted {@link EnchantRecipes} validation supply authority; names, lore,
 * native glint and native stored enchantments do not. All operations touching stacks
 * require the classic Paper server thread. Only generated lore/glint and the owned
 * root are replaced during an edit; foreign components and the existing name survive.
 */
public final class EnchantBookCodec {
    /**
     * Owned nested book root; its exact schema is independent of the weapon root.
     */
    public static final NamespacedKey ROOT = key("enchant_book");
    private static final NamespacedKey SCHEMA = key("schema"), CATALOG = key("catalog"), ENCHANT = key("enchant"), LEVEL = key("level");
    /**
     * Separates validated custom books, malformed owned roots and ordinary items.
     */
    public sealed interface Read {
        /**
         * Trusted single-enchantment book selection.
         * @param book validated catalog revision, enchant ID and legal level
         */
        record Valid(EnchantBook book) implements Read {}
        /**
         * Present custom root that failed schema, material, amount or recipe validation.
         * @param reason diagnostic failure, never a replacement enchant selection
         */
        record Invalid(String reason) implements Read {}
        /**
         * Absent book root, including null or metadata-free stacks; display text is ignored.
         */
        record Ordinary() implements Read {}
    }
    private final EnchantRecipes recipes;

    /**
     * Uses a trusted catalog/router for legal levels, categories and exact revisions.
     * @param registry registry used by book recipes and weapon consumers
     */
    public EnchantBookCodec(ItemRegistry registry) { recipes = new EnchantRecipes(registry); }

    /**
     * Encodes a new amount-one enchanted book and verifies its decoded selection.
     * @param book full trusted selection to encode
     * @return new stack with generated name/lore/glint and owned PDC
     * @throws IllegalArgumentException if recipe/catalog validation fails
     * @throws IllegalStateException if called off the server thread
     */
    public ItemStack encode(EnchantBook book) {
        requireThread();
        var stack = new ItemStack(Material.ENCHANTED_BOOK);
        return write(stack, book, false);
    }

    /**
     * Clones a single valid left book while retaining its catalog and enchant ID.
     * This primitive validates the resulting level; recipe improvement/cost policy is
     * owned by {@link EnchantRecipes}, not by the clone operation.
     * @param original amount-one valid custom book, never modified
     * @param book replacement selection with the same catalog and enchant ID
     * @return separately owned edited stack
     * @throws IllegalArgumentException for invalid original, changed identity or illegal level
     * @throws IllegalStateException if called off the server thread
     */
    public ItemStack edit(ItemStack original, EnchantBook book) {
        requireThread();
        if (!(decode(original) instanceof Read.Valid before) || original.getAmount() != 1
                || !before.book().catalogRevision().equals(book.catalogRevision())
                || !before.book().enchantId().equals(book.enchantId()))
            throw new IllegalArgumentException("Invalid left book edit");
        return write(original.clone(), book, true);
    }

    /**
     * Writes validated owned metadata and generated lore, then decodes the final stack.
     * @param stack caller-owned new/clone stack to mutate
     * @param book replacement selection
     * @param preserveName true for edits; false generates the initial book name
     * @return the same mutated stack after exact selection validation
     * @throws IllegalArgumentException for invalid recipes or final codec mismatch
     */
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

    /**
     * Classifies a candidate without modifying it; valid right books may be stacked.
     * The owned root must contain exactly schema/catalog/enchant/level with matching
     * types and schema1. Unsupported catalog/category/level data fails closed.
     * @param stack nullable item to inspect
     * @return non-null ordinary, invalid or validated-book result
     * @throws IllegalStateException if called off the server thread
     */
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

    /**
     * Creates a literal non-italic lore line; no markup is evaluated.
     * @param text generated display text
     * @param color visual enchant category
     * @return new component
     */
    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    /**
     * Constructs an owned local book field key.
     * @param value constant local key
     * @return key in the onlydragons namespace
     */
    private static NamespacedKey key(String value) { return new NamespacedKey("onlydragons", value); }
    /**
     * Rejects asynchronous stack/PDC access; this codec has no scheduling fallback.
     */
    private static void requireThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Book access belongs to the server thread");
    }
}
