package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Minimal offensive definition. T02 supplies PDC codecs and registry compatibility checks. */
public record WeaponDefinition(String id, int schemaVersion, String revision, FiringMode firingMode,
                               double baseDamage, List<StatModifier> statModifiers, List<Enchantment> enchantments) {
    public enum FiringMode { DRAWN_BOW, SHORTBOW }
    public enum EnchantmentKind { ORDINARY, ULTIMATE }

    /** The trusted registry determines kind and legal levels; item input cannot self-classify. */
    public record Enchantment(String id, int level, EnchantmentKind kind) {
        public Enchantment {
            DomainChecks.text(id, "enchantment id");
            Objects.requireNonNull(kind, "kind");
            if (level < 1) throw new IllegalArgumentException("Enchantment level must be positive");
        }
    }

    public WeaponDefinition {
        DomainChecks.text(id, "id");
        DomainChecks.text(revision, "revision");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(firingMode, "firingMode");
        DomainChecks.nonNegative(baseDamage, "baseDamage");
        statModifiers = List.copyOf(statModifiers).stream().sorted(StatModifier.EXPLANATION_ORDER).toList();
        enchantments = validatedEnchantments(enchantments);
    }

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
