package com.kaveenk.onlydragons.domain.item.anvil;

import com.kaveenk.onlydragons.domain.item.EnchantDefinition;
import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.HashMap;
import java.util.Objects;

/** Pure development calibration. Successful previews have no inventory or XP side effects. */
public final class EnchantRecipes {
    public record Applied(ItemInstance item, int levels) {}
    public record Combined(EnchantBook book, int levels) {}
    private final ItemRegistry registry;

    public EnchantRecipes(ItemRegistry registry) { this.registry = Objects.requireNonNull(registry); }

    public EnchantDefinition validate(EnchantBook book) {
        var enchant = registry.catalog(book.catalogRevision()).enchant(book.enchantId());
        if (!enchant.available() || !enchant.levelModifiers().containsKey(book.level()))
            throw new IllegalArgumentException("Unavailable enchant or invalid book level");
        return enchant;
    }

    public Applied apply(ItemInstance left, EnchantBook right, boolean rename) {
        validate(right);
        var resolved = registry.resolve(left);
        var enchant = registry.catalog(left.registryRevision()).enchant(right.enchantId());
        var target = new EnchantTarget(EnchantTarget.Category.BOW, EnchantTarget.Slot.MAIN_HAND,
                resolved.definition().weapon().firingMode());
        if (!target.supports(enchant)) throw new IllegalArgumentException("Enchant is unavailable or incompatible with this item");
        int level = improved(left.enchantLevels().getOrDefault(right.enchantId(), 0), right.level(), enchant);
        var selections = new HashMap<>(left.enchantLevels());
        selections.put(right.enchantId(), level);
        var result = registry.edit(left, selections, left.rolledModifierIds());
        return new Applied(result, cost(enchant, level) + (rename ? 1 : 0));
    }

    public Combined combine(EnchantBook left, EnchantBook right, boolean rename) {
        var enchant = validate(left);
        validate(right);
        if (!left.enchantId().equals(right.enchantId())) throw new IllegalArgumentException("Combine books of the same enchant only");
        int level = improved(left.level(), right.level(), enchant);
        var result = new EnchantBook(left.catalogRevision(), left.enchantId(), level);
        validate(result);
        return new Combined(result, cost(enchant, level) + (rename ? 1 : 0));
    }

    public static int cost(EnchantDefinition enchant, int level) {
        if (!enchant.levelModifiers().containsKey(level)) throw new IllegalArgumentException("Invalid cost level");
        return level * (enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE ? 4 : 2);
    }

    public static String rename(String name) {
        Objects.requireNonNull(name);
        if (name.length() > 50 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))
            throw new IllegalArgumentException("Use a plain name of at most 50 characters");
        return name.strip();
    }

    private static int improved(int left, int right, EnchantDefinition enchant) {
        if (!enchant.levelModifiers().containsKey(right)) throw new IllegalArgumentException("Unsupported input level");
        int result = left == right ? left + 1 : Math.max(left, right);
        if (result <= left) throw new IllegalArgumentException("This book would not improve the left item");
        if (!enchant.levelModifiers().containsKey(result)) throw new IllegalArgumentException("Combination exceeds the maximum level");
        return result;
    }
}
