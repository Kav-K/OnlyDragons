package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned development data, not final balance or grants. Resolved definitions feed the existing firing/enchant consumers. */
public final class CalibrationLoadouts {
    /**
     * Preserved v2 item-catalog identity with six supported enchant descriptors.
     */
    public static final String REVISION = "calibration-items-v2";
    private CalibrationLoadouts() {}

    /**
     * Builds the preserved ten-definition v2 catalog: eight original drawn controls, returning
     * Tracer v2 and shortbow v1. Base damage is 100, calibration CD 50 comes from the stat profile;
     * Vicious contributes its level once as flat Ferocity. No items are granted.
     * @return new preserved v2 catalog containing the original controls and explicit Tracer-v2/shortbow-v1 definitions
     */
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

    /**
     * V3 identity with Overload/Gravity consumers and explicit unavailable Quiver/Flame descriptors.
     */
    public static final String EXPANDED_REVISION = "calibration-items-v3";

    /**
     * Production catalog exposes all three explicit histories. No automatic identity migration.
     * <p>
     * Builds v2, v3, v4 in precedence order and returns an exact-revision router. Old identities
     * retain their tables; newest aggregate descriptors do not change old item compatibility.
     * @return new flat exact-revision router over v2, v3 and v4; old identities retain their own tables
     */
    public static ItemRegistry compatibleRegistry() {
        ItemRegistry legacy = registry();
        ItemRegistry expanded = expandedRegistry(legacy);
        ItemRegistry fire = fireRegistry(expanded);
        return new ItemRegistry(List.of(legacy, expanded, fire));
    }

    /**
     * Builds the standalone eleven-definition v3 catalog, preserving v2 as a separate history.
     * @return new standalone v3 catalog with expanded bow effects and preserved unavailable controls
     */
    public static ItemRegistry expandedRegistry() {
        return expandedRegistry(registry());
    }

    /**
     * Derives selected legacy presets into distinct v3 IDs, adds Overload/Gravity controls and
     * trusted Overload CC/CD contributions. Quiver and Flame remain unavailable in this catalog.
     */
    private static ItemRegistry expandedRegistry(ItemRegistry legacy) {
        var enchants = new ArrayList<>(legacy.enchantments().values());
        for (String id : List.of("overload", "gravity", "infinite_quiver", "flame")) {
            var levels = new LinkedHashMap<Integer, List<StatModifier>>();
            int max = switch (id) {
                case "overload" -> 5;
                case "gravity" -> 6;
                case "infinite_quiver" -> 10;
                default -> 2;
            };
            for (int level = 1; level <= max; level++) {
                levels.put(level, id.equals("overload")
                        ? List.of(flat("enchant:overload", StatKey.CRIT_CHANCE, level),
                                  flat("enchant:overload", StatKey.CRIT_DAMAGE, level)) : List.of());
            }
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

    /**
     * V4 catalog identity enabling all ten enchant consumers and the seven held-shortbow definitions.
     */
    public static final String FIRE_REVISION = "calibration-items-v4";

    /**
     * Explicit consumer-capable catalog; v2/v3 identities and unavailable controls remain unchanged.
     * <p>
     * Builds the standalone consumer-capable v4 catalog with six fire/ammo controls and seven
     * held presets; no existing item is migrated or edited.
     * @return new standalone v4 catalog with active fire/ammo consumers and declared held presets
     */
    public static ItemRegistry fireRegistry() {
        return fireRegistry(expandedRegistry());
    }

    /**
     * Copies expanded descriptors as available, adds distinct v4 definitions and appends
     * {@link ShortbowLoadouts} in published kit order. Calibration tables remain compiled data.
     */
    private static ItemRegistry fireRegistry(ItemRegistry expanded) {
        var enchants = expanded.enchantments().values().stream()
                .map(enchant -> new EnchantDefinition(enchant.id(), enchant.displayName(), enchant.kind(),
                        enchant.compatibleModes(), enchant.levelModifiers(), true)).toList();
        var definitions = new ArrayList<ItemDefinition>();
        for (String id : List.of("ordinary_v4", "shortbow_v4", "quiver_v4", "flame_v4", "duplex_flame_v4", "tempo_flame_v4")) {
            var selected = new ArrayList<WeaponDefinition.Enchantment>();
            if (id.equals("quiver_v4") || id.equals("shortbow_v4")) {
                selected.add(new WeaponDefinition.Enchantment("infinite_quiver", 10, WeaponDefinition.EnchantmentKind.ORDINARY));
            }
            if (id.contains("flame") || id.equals("shortbow_v4")) {
                selected.add(new WeaponDefinition.Enchantment("flame", 2, WeaponDefinition.EnchantmentKind.ORDINARY));
            }
            if (id.equals("duplex_flame_v4")) {
                selected.add(enchant("duplex", true));
            }
            if (id.equals("tempo_flame_v4")) {
                selected.add(enchant("fatal_tempo", true));
            }
            definitions.add(new ItemDefinition(new WeaponDefinition(id, ItemRegistry.SCHEMA_VERSION, FIRE_REVISION,
                    id.equals("shortbow_v4") ? WeaponDefinition.FiringMode.SHORTBOW : WeaponDefinition.FiringMode.DRAWN_BOW,
                    100, id.equals("tempo_flame_v4") ? List.of(flat("item:tempo_flame_v4", StatKey.FEROCITY, 25)) : List.of(), selected),
                    id.replace('_', ' ') + " (Development)", "BOW", Set.of()));
        }
        definitions.addAll(ShortbowLoadouts.definitions());
        return new ItemRegistry(FIRE_REVISION, definitions, enchants, Map.of());
    }

    /**
     * Builds a 100-damage drawn control with explicit CC/Ferocity flat sources and no extra CD.
     * The supplied enchant list is the default selection, not an effect execution request.
     */
    private static ItemDefinition bow(String id, String name, double crit, double ferocity,
                                      List<WeaponDefinition.Enchantment> enchants) {
        return new ItemDefinition(new WeaponDefinition(id, ItemRegistry.SCHEMA_VERSION, REVISION,
                WeaponDefinition.FiringMode.DRAWN_BOW, 100,
                List.of(flat("item:" + id, StatKey.CRIT_CHANCE, crit),
                        flat("item:" + id, StatKey.FEROCITY, ferocity)), enchants), name, "BOW", Set.of());
    }

    /**
     * Creates order-zero calibration contribution in the stat's native unit using an explicit source ID.
     */
    private static StatModifier flat(String source, StatKey key, double amount) {
        return new StatModifier(source, key, ModifierOperation.FLAT, amount, 0);
    }

    /**
     * Creates the common level-V default with an explicitly trusted ultimate/ordinary category.
     */
    private static WeaponDefinition.Enchantment enchant(String id, boolean ultimate) {
        return new WeaponDefinition.Enchantment(id, 5, ultimate
                ? WeaponDefinition.EnchantmentKind.ULTIMATE : WeaponDefinition.EnchantmentKind.ORDINARY);
    }
}
