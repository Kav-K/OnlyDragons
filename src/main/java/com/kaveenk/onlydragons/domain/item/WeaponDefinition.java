package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Minimal offensive definition. T02 supplies PDC codecs and registry compatibility checks.
 * <p>
 * Consumed by {@link com.kaveenk.onlydragons.domain.stats.StatSnapshotFactory} and wrapped by
 * {@link ItemDefinition}. No native item, grant or firing event is created here.
 * @param id nonblank offensive definition ID
 * @param schemaVersion positive schema, subject to stricter registry support
 * @param revision nonblank caller-maintained definition label
 * @param firingMode nonnull drawn/instant firing selection
 * @param baseDamage finite nonnegative damage points, supplied as a base exactly once
 * @param statModifiers copied canonical contributions, including duplicates if supplied
 * @param enchantments copied ID-sorted selections with unique IDs and at most one ultimate
 */
public record WeaponDefinition(String id, int schemaVersion, String revision, FiringMode firingMode,
                               double baseDamage, List<StatModifier> statModifiers, List<Enchantment> enchantments) {
    /**
     * DRAWN_BOW uses native release/force; SHORTBOW uses owned instant input/cooldown. Both currently
     * use BOW items; this enum does not start firing or define projectile count.
     */
    public enum FiringMode {
        /** Native drawn-bow trigger policy. */ DRAWN_BOW,
        /** Plugin-controlled shortbow trigger policy. */ SHORTBOW
    }
    /**
     * Trusted ordinary/ultimate category; only one ULTIMATE is permitted per weapon, regardless of level.
     */
    public enum EnchantmentKind {
        /** Enchant category that can coexist with other compatible ordinary enchants. */ ORDINARY,
        /** Exclusive category permitting at most one ultimate enchant per item. */ ULTIMATE
    }

    /**
     * The trusted registry determines kind and legal levels; item input cannot self-classify.
     * @param id nonblank effect ID; strict syntax/existence is checked by the registry
     * @param level positive selected level; supported maxima belong to the trusted descriptor
     * @param kind nonnull trusted category, never accepted from untrusted PDC as authority
     */
    public record Enchantment(String id, int level, EnchantmentKind kind) {
        /**
         * Validates structural selection data; does not verify a registry, compatibility or an effect table.
         */
        public Enchantment {
            DomainChecks.text(id, "enchantment id");
            Objects.requireNonNull(kind, "kind");
            if (level < 1) throw new IllegalArgumentException("Enchantment level must be positive");
        }
    }

    /**
     * Validates identity/numbers and freezes ordered modifiers/enchants. Catalog-specific schema,
     * material, effect availability and allowed rolls are checked by ItemRegistry.
     */
    public WeaponDefinition {
        DomainChecks.text(id, "id");
        DomainChecks.text(revision, "revision");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(firingMode, "firingMode");
        DomainChecks.nonNegative(baseDamage, "baseDamage");
        statModifiers = List.copyOf(statModifiers).stream().sorted(StatModifier.EXPLANATION_ORDER).toList();
        enchantments = validatedEnchantments(enchantments);
    }

    /**
     * Copies and sorts a nonnull list by case-sensitive ID, rejecting null entries, duplicate IDs
     * and multiple ultimates. Returns immutable selections; supported levels still require a catalog.
     */
    public static List<Enchantment> validatedEnchantments(List<Enchantment> enchantments) {
        var ordered = List.copyOf(enchantments).stream().sorted(Comparator.comparing(Enchantment::id)).toList();
        var ids = new HashSet<String>();
        int ultimates = 0;
        for (Enchantment enchantment : ordered) {
            if (!ids.add(enchantment.id())) throw new IllegalArgumentException("Duplicate enchantment " + enchantment.id());
            if (enchantment.kind() == EnchantmentKind.ULTIMATE) ultimates++;
        }
        if (ultimates > 1) throw new IllegalArgumentException("A weapon can have only one ultimate enchantment");
        return ordered;
    }
}
