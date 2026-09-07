package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned development data, not final balance or grants. Effects are implemented by later tasks. */
public final class CalibrationLoadouts {
    public static final String REVISION = "calibration-items-v2";
    private CalibrationLoadouts() {}

    public static ItemRegistry registry() {
        var enchants = new ArrayList<EnchantDefinition>();
        for (String id : List.of("dragon_tracer", "duplex", "fatal_tempo", "power", "vicious", "snipe")) {
            var levels = new LinkedHashMap<Integer, List<StatModifier>>();
            int max = id.equals("power") ? 7 : id.equals("snipe") ? 4 : 5;
            for (int level = 1; level <= max; level++) {
                levels.put(level, id.equals("vicious")
                        ? List.of(flat("enchant:vicious", StatKey.FEROCITY, level)) : List.of());
            }
            boolean ultimate = id.equals("duplex") || id.equals("fatal_tempo");
            enchants.add(new EnchantDefinition(id, switch (id) {
                case "dragon_tracer" -> "Dragon Tracer";
                case "fatal_tempo" -> "Fatal Tempo";
                default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
            }, ultimate ? WeaponDefinition.EnchantmentKind.ULTIMATE : WeaponDefinition.EnchantmentKind.ORDINARY,
                    Set.of(WeaponDefinition.FiringMode.DRAWN_BOW, WeaponDefinition.FiringMode.SHORTBOW), levels));
        }
        return new ItemRegistry(REVISION, List.of(
                new ItemDefinition(new WeaponDefinition("tracer_return_v2", ItemRegistry.SCHEMA_VERSION, "tracer-return-v2",
                        WeaponDefinition.FiringMode.DRAWN_BOW, 100, List.of(),
                        List.of(enchant("dragon_tracer", false), enchant("duplex", true))),
                        "Returning Tracer v2 (Development)", "BOW", Set.of()),
                bow("ordinary", "Calibration Bow", 0, 0, List.of()),
                bow("crit", "Critical Calibration Bow", 100, 0, List.of()),
                bow("ferocity_25", "25 Ferocity Bow", 0, 25, List.of()),
                bow("ferocity_100", "100 Ferocity Bow", 0, 100, List.of()),
                bow("ferocity_500", "Cap Calibration Bow", 0, 500, List.of()),
                bow("tracer", "Tracer Calibration Bow", 0, 0, List.of(enchant("dragon_tracer", false))),
                bow("duplex", "Duplex Calibration Bow", 0, 0, List.of(enchant("duplex", true))),
                bow("fatal_tempo", "Tempo Calibration Bow", 0, 25, List.of(enchant("fatal_tempo", true))),
                new ItemDefinition(new WeaponDefinition("shortbow_v1", ItemRegistry.SCHEMA_VERSION, "shortbow-calibration-v1",
                        WeaponDefinition.FiringMode.SHORTBOW, 100, List.of(), List.of(enchant("duplex", true))),
                        "Shortbow Calibration v1", "BOW", Set.of())
        ), enchants, Map.of());
    }

    public static final String EXPANDED_REVISION = "calibration-items-v3";

    /** Production catalog exposes both explicit histories. No automatic identity migration. */
    public static ItemRegistry compatibleRegistry() { return new ItemRegistry(registry(), expandedRegistry()); }

    public static ItemRegistry expandedRegistry() {
        var legacy = registry();
        var enchants = new ArrayList<>(legacy.enchantments().values());
        for (String id : List.of("overload", "gravity", "infinite_quiver", "flame")) {
            var levels = new LinkedHashMap<Integer, List<StatModifier>>();
            int max = switch (id) { case "overload" -> 5; case "gravity" -> 6; case "infinite_quiver" -> 10; default -> 2; };
            for (int level = 1; level <= max; level++) levels.put(level, id.equals("overload")
                    ? List.of(flat("enchant:overload", StatKey.CRIT_CHANCE, level),
                              flat("enchant:overload", StatKey.CRIT_DAMAGE, level)) : List.of());
            enchants.add(new EnchantDefinition(id, switch (id) {
                case "infinite_quiver" -> "Infinite Quiver";
                default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
            }, WeaponDefinition.EnchantmentKind.ORDINARY,
                    Set.of(WeaponDefinition.FiringMode.DRAWN_BOW, WeaponDefinition.FiringMode.SHORTBOW),
                    levels, !id.equals("infinite_quiver") && !id.equals("flame")));
        }
        var definitions = new ArrayList<ItemDefinition>();
        for (String id : List.of("ordinary", "crit", "ferocity_25", "ferocity_100", "ferocity_500", "tracer", "duplex", "fatal_tempo", "shortbow_v1")) {
            var item = legacy.definitions().get(id);
            var weapon = item.weapon();
            definitions.add(new ItemDefinition(new WeaponDefinition(weapon.id() + "_v3", ItemRegistry.SCHEMA_VERSION, EXPANDED_REVISION,
                    weapon.firingMode(), weapon.baseDamage(), weapon.statModifiers(), weapon.enchantments()),
                    item.displayName() + " v3", item.material(), item.allowedRolls()));
        }
        definitions.add(new ItemDefinition(new WeaponDefinition("overload_v3", ItemRegistry.SCHEMA_VERSION, EXPANDED_REVISION,
                WeaponDefinition.FiringMode.DRAWN_BOW, 100,
                List.of(flat("item:overload_v3", StatKey.CRIT_CHANCE, 195)),
                List.of(new WeaponDefinition.Enchantment("overload", 5, WeaponDefinition.EnchantmentKind.ORDINARY))),
                "Overload Calibration v3", "BOW", Set.of()));
        definitions.add(new ItemDefinition(new WeaponDefinition("gravity_v3", ItemRegistry.SCHEMA_VERSION, EXPANDED_REVISION,
                WeaponDefinition.FiringMode.DRAWN_BOW, 100, List.of(),
                List.of(new WeaponDefinition.Enchantment("gravity", 6, WeaponDefinition.EnchantmentKind.ORDINARY))),
                "Gravity Calibration v3", "BOW", Set.of()));
        return new ItemRegistry(EXPANDED_REVISION, definitions, enchants, Map.of());
    }

    private static ItemDefinition bow(String id, String name, double crit, double ferocity,
                                      List<WeaponDefinition.Enchantment> enchants) {
        return new ItemDefinition(new WeaponDefinition(id, ItemRegistry.SCHEMA_VERSION, REVISION,
                WeaponDefinition.FiringMode.DRAWN_BOW, 100,
                List.of(flat("item:" + id, StatKey.CRIT_CHANCE, crit),
                        flat("item:" + id, StatKey.FEROCITY, ferocity)), enchants), name, "BOW", Set.of());
    }

    private static StatModifier flat(String source, StatKey key, double amount) {
        return new StatModifier(source, key, ModifierOperation.FLAT, amount, 0);
    }

    private static WeaponDefinition.Enchantment enchant(String id, boolean ultimate) {
        return new WeaponDefinition.Enchantment(id, 5, ultimate
                ? WeaponDefinition.EnchantmentKind.ULTIMATE : WeaponDefinition.EnchantmentKind.ORDINARY);
    }
}
