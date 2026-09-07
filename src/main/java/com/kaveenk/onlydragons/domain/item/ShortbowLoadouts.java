package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.List;
import java.util.Set;

/** Additive v4 human rehearsal presets. Values are versioned development calibration. */
public final class ShortbowLoadouts {
    public static final String REVISION = "held-shortbows-v1";
    private static final List<ItemDefinition> DEFINITIONS = List.of(
            bow("drawn_training_v4", "Drawn Training Bow", false, 0, 0, List.of()),
            bow("swift_shortbow_v4", "Swift Shortbow", true, 100, 0, List.of()),
            bow("volley_shortbow_v4", "Volley Shortbow", true, 400, 0, List.of()),
            bow("volley_ferocity25_v4", "Volley · 25 Ferocity (25% extra hit)", true, 400, 25, List.of()),
            bow("volley_ferocity100_v4", "Volley · 100 Ferocity (one guaranteed extra hit)", true, 400, 100, List.of()),
            bow("volley_duplex_v4", "Returning Volley · Tracer V + Duplex V", true, 400, 25, combined("duplex")),
            bow("volley_tempo_v4", "Returning Volley · Tracer V + Fatal Tempo V", true, 400, 25, combined("fatal_tempo")));

    private ShortbowLoadouts() {}

    public static List<ItemDefinition> definitions() { return DEFINITIONS; }
    public static List<String> ids() { return DEFINITIONS.stream().map(item -> item.weapon().id()).toList(); }

    /** Trusted definition selection; enchant edits alone cannot select a steering profile. */
    public static boolean returningTracer(WeaponDefinition trusted) {
        return trusted.revision().equals(REVISION)
                && ids().contains(trusted.id());
    }

    private static ItemDefinition bow(String id, String name, boolean instant, int speed, int ferocity,
                                      List<WeaponDefinition.Enchantment> enchants) {
        return new ItemDefinition(new WeaponDefinition(id, ItemRegistry.SCHEMA_VERSION, REVISION,
                instant ? WeaponDefinition.FiringMode.SHORTBOW : WeaponDefinition.FiringMode.DRAWN_BOW,
                100, List.of(new StatModifier("item:" + id, StatKey.ATTACK_SPEED, ModifierOperation.FLAT, speed, 0),
                             new StatModifier("item:" + id, StatKey.FEROCITY, ModifierOperation.FLAT, ferocity, 0)),
                enchants), name, "BOW", Set.of());
    }

    private static List<WeaponDefinition.Enchantment> combined(String ultimate) {
        return List.of(new WeaponDefinition.Enchantment("dragon_tracer", 5, WeaponDefinition.EnchantmentKind.ORDINARY),
                new WeaponDefinition.Enchantment(ultimate, 5, WeaponDefinition.EnchantmentKind.ULTIMATE),
                new WeaponDefinition.Enchantment("infinite_quiver", 10, WeaponDefinition.EnchantmentKind.ORDINARY),
                new WeaponDefinition.Enchantment("flame", 2, WeaponDefinition.EnchantmentKind.ORDINARY));
    }
}
