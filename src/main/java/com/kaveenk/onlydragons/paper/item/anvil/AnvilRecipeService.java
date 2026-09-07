package com.kaveenk.onlydragons.paper.item.anvil;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantRecipes;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

/**
 * Builds isolated native-anvil candidates without inventory or XP mutation.
 * Any owned weapon/book root, including corrupt metadata, enters the managed boundary;
 * entirely ordinary inputs remain Paper's recipe. Uses {@link WeaponItemCodec} and
 * {@link EnchantBookCodec} on the server thread and never infers identity from lore.
 */
public final class AnvilRecipeService {
    /**
     * Recipe classification and candidate; this record does not clone mutable output.
     * @param managed whether any input belongs to the custom metadata boundary
     * @param output caller-owned result stack, null for ordinary or rejected recipes
     * @param cost XP levels; zero for ordinary, -1 for a managed rejection
     * @param rightCount number of right books consumed on native success, zero for rename-only
     * @param rejection empty on ordinary/success, otherwise a diagnostic reason
     */
    public record Preview(boolean managed, ItemStack output, int cost, int rightCount, String rejection) {}
    private final WeaponItemCodec weapons;
    private final EnchantBookCodec books;
    private final EnchantRecipes recipes;

    /**
     * Binds book and weapon validation to the same trusted registry/router.
     * @param registry catalog authority for exact revisions and allowed enchant targets
     */
    public AnvilRecipeService(ItemRegistry registry) {
        weapons = new WeaponItemCodec(registry);
        books = new EnchantBookCodec(registry);
        recipes = new EnchantRecipes(registry);
    }

    /**
     * Resolves a rename, same-enchant book improvement or bow application on cloned data.
     * The left input must be one valid managed item. A nonempty right input must be a
     * valid custom book; exactly one is consumed on success. All original metadata and
     * stacks remain untouched. Recipe failures return a managed rejection.
     * @param left nullable left stack; amount must be one for a managed candidate
     * @param right nullable right book stack; empty permits rename-only
     * @param renameText desired plain name, null to retain the effective current name
     * @return non-null ordinary, rejected or successful candidate
     * @throws IllegalStateException if called outside the Paper server thread
     */
    public Preview preview(ItemStack left, ItemStack right, String renameText) {
        var weapon = weapons.decode(left);
        var leftBook = books.decode(left);
        var rightBook = books.decode(right);
        boolean managed = !(weapon instanceof ItemReadResult.NotManaged) || !(leftBook instanceof EnchantBookCodec.Read.Ordinary)
                || !(rightBook instanceof EnchantBookCodec.Read.Ordinary)
                || !(weapons.decode(right) instanceof ItemReadResult.NotManaged);
        if (!managed) return new Preview(false, null, 0, 0, "");
        try {
            if (empty(left) || left.getAmount() != 1) throw new IllegalArgumentException("Use exactly one managed item on the left");
            if (!(weapon instanceof ItemReadResult.Valid) && !(leftBook instanceof EnchantBookCodec.Read.Valid))
                throw new IllegalArgumentException("The left item must be a valid managed bow or enchant book");
            String name = renameText == null ? currentName(left) : EnchantRecipes.rename(renameText);
            boolean rename = !Objects.equals(name, currentName(left));
            ItemStack output;
            int cost;
            int consume;
            if (empty(right)) {
                if (!rename) throw new IllegalArgumentException("No enchant or name change");
                output = left.clone(); cost = 1; consume = 0;
            } else {
                if (!(rightBook instanceof EnchantBookCodec.Read.Valid valid))
                    throw new IllegalArgumentException("Use a valid custom enchant book on the right");
                if (weapon instanceof ItemReadResult.Valid validWeapon) {
                    var result = recipes.apply(validWeapon.item().instance(), valid.book(), rename);
                    output = weapons.edit(left, result.item()); cost = result.levels();
                } else {
                    var result = recipes.combine(((EnchantBookCodec.Read.Valid) leftBook).book(), valid.book(), rename);
                    output = books.edit(left, result.book()); cost = result.levels();
                }
                consume = 1;
            }
            if (rename) output.editMeta(meta -> meta.displayName(name.isEmpty() ? null
                    : Component.text(name).decoration(TextDecoration.ITALIC, false)));
            // Renames and presentation must never alter the validated authority.
            if (weapon instanceof ItemReadResult.Valid && !(weapons.decode(output) instanceof ItemReadResult.Valid)
                    || leftBook instanceof EnchantBookCodec.Read.Valid && !(books.decode(output) instanceof EnchantBookCodec.Read.Valid))
                throw new IllegalArgumentException("Invalid final anvil item");
            return new Preview(true, output, cost, consume, "");
        } catch (IllegalArgumentException invalid) {
            return new Preview(true, null, -1, 0, invalid.getMessage());
        }
    }

    /**
     * Flattens a custom name or effective default for rename equality, not identity.
     * @param item nonempty validated left item
     * @return literal name used only to decide whether the rename costs a level
     */
    private static String currentName(ItemStack item) {
        var name = item.getItemMeta().displayName();
        return name == null ? PlainTextComponentSerializer.plainText().serialize(item.effectiveName())
                : PlainTextComponentSerializer.plainText().serialize(name);
    }
    /**
     * Normalizes the three native representations of an empty slot.
     * @param item nullable candidate
     * @return true for null, air or zero amount
     */
    static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() == 0; }
}
