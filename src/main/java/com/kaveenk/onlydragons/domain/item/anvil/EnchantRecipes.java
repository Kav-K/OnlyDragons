package com.kaveenk.onlydragons.domain.item.anvil;

import com.kaveenk.onlydragons.domain.item.EnchantDefinition;
import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.HashMap;
import java.util.Objects;

/** Pure development calibration. Successful previews have no inventory or XP side effects. */
public final class EnchantRecipes {
    /**
     * Preview value; native anvil extraction owns the eventual single inventory/XP debit.
     * @param item validated replacement item preserving the left identity and rolls
     * @param levels XP levels required, not raw XP points
     */
    public record Applied(ItemInstance item, int levels) {}
    /**
     * Preview of one improving same-enchant combination, without granting the output.
     * @param book validated result retaining the left book catalog
     * @param levels XP levels required, not raw XP points
     */
    public record Combined(EnchantBook book, int levels) {}
    private final ItemRegistry registry;

    /**
     * Retains the nonnull immutable catalog/router; no live inventory or scheduler state is held.
     * @param registry nonnull immutable exact-revision item catalog/router
     * @throws NullPointerException if registry is null
     */
    public EnchantRecipes(ItemRegistry registry) { this.registry = Objects.requireNonNull(registry); }

    /**
     * Resolves the nonnull book in its exact declared catalog and checks availability and explicit
     * level-table membership. Returns the trusted descriptor; invalid references/table selections reject.
     * @param book nonnull book carrying an exact catalog revision, enchant ID and level
     * @return trusted available descriptor supporting the exact book level
     * @throws IllegalArgumentException if catalog/ID resolution fails or the enchant/level is unavailable
     */
    public EnchantDefinition validate(EnchantBook book) {
        var enchant = registry.catalog(book.catalogRevision()).enchant(book.enchantId());
        if (!enchant.available() || !enchant.levelModifiers().containsKey(book.level()))
            throw new IllegalArgumentException("Unavailable enchant or invalid book level");
        return enchant;
    }

    /**
     * Validates both inputs and compatibility against the left item's original catalog, then
     * replaces its complete enchant selection while preserving identity/rolls. Equal levels advance
     * one; unequal levels take the larger only if improving. Conflicts, no-ops and caps reject.
     * @param left nonnull untrusted instance to validate, never mutated
     * @param right nonnull book, validated in its own catalog and again against the target descriptor
     * @param rename whether the adapter has established an actual plain-name change (adds one level)
     * @return immutable replacement and XP-level cost; no inventory/XP mutation
     */
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

    /**
     * Validates both books; IDs must match, but catalog labels may differ. The result retains
     * the left catalog and must improve within that catalog's explicit table. Rename adds one XP
     * level when the adapter reports a real change; no output item is granted.
     * @param left validated left book whose catalog controls the improved result
     * @param right validated right book of the same enchant ID
     * @param rename whether the adapter observed a real rename, adding one XP level
     * @return improved book value and XP-level cost without a native inventory grant
     * @throws IllegalArgumentException if references differ, the resulting level is unsupported or no improvement exists
     */
    public Combined combine(EnchantBook left, EnchantBook right, boolean rename) {
        var enchant = validate(left);
        validate(right);
        if (!left.enchantId().equals(right.enchantId())) throw new IllegalArgumentException("Combine books of the same enchant only");
        int level = improved(left.level(), right.level(), enchant);
        var result = new EnchantBook(left.catalogRevision(), left.enchantId(), level);
        validate(result);
        return new Combined(result, cost(enchant, level) + (rename ? 1 : 0));
    }

    /**
     * Returns resulting level×2 for ordinary or ×4 for ultimate, in XP levels. The level must
     * exist in the descriptor table; this helper does not independently check availability.
     * @param enchant trusted descriptor supplying explicit levels and ordinary/ultimate kind
     * @param level resulting level that must occur in that descriptor table
     * @return XP levels: two times ordinary level or four times ultimate level, excluding rename
     * @throws IllegalArgumentException if the resulting level is absent from the table
     */
    public static int cost(EnchantDefinition enchant, int level) {
        if (!enchant.levelModifiers().containsKey(level)) throw new IllegalArgumentException("Invalid cost level");
        return level * (enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE ? 4 : 2);
    }

    /**
     * Rejects null, ISO control characters, section sign and input length above 50 UTF-16 code
     * units before stripping surrounding whitespace. Returns plain stripped text, possibly empty;
     * the adapter decides whether it differs from the existing name.
     * @param name nonnull unformatted rename input, at most 50 UTF-16 code units before stripping
     * @return plain stripped name, possibly empty
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if input is too long or contains ISO controls or section sign
     */
    public static String rename(String name) {
        Objects.requireNonNull(name);
        if (name.length() > 50 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))
            throw new IllegalArgumentException("Use a plain name of at most 50 characters");
        return name.strip();
    }

    /**
     * Requires a supported right level; equal levels add one, otherwise use the larger. Rejects
     * no improvement and any result missing from the target's explicit level table.
     */
    private static int improved(int left, int right, EnchantDefinition enchant) {
        if (!enchant.levelModifiers().containsKey(right)) throw new IllegalArgumentException("Unsupported input level");
        int result = left == right ? left + 1 : Math.max(left, right);
        if (result <= left) throw new IllegalArgumentException("This book would not improve the left item");
        if (!enchant.levelModifiers().containsKey(result)) throw new IllegalArgumentException("Combination exceeds the maximum level");
        return result;
    }
}
