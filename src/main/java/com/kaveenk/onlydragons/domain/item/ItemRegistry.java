package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.kaveenk.onlydragons.domain.item.ItemValidationException.Code.*;

/** Immutable trusted catalog shared by grant/edit/load boundaries. No server dependencies. */
public final class ItemRegistry {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 32;

    public record ResolvedItem(ItemInstance instance, ItemDefinition definition,
                               List<WeaponDefinition.Enchantment> enchantments,
                               List<StatModifier> statModifiers) {
        public ResolvedItem {
            enchantments = List.copyOf(enchantments);
            statModifiers = List.copyOf(statModifiers);
        }

        /**
         * Pass this projection once to the stat snapshot factory. It already includes all
         * definition, enchant and roll modifiers; additional sources must be external gear/buffs.
         */
        public WeaponDefinition resolvedWeapon() {
            WeaponDefinition base = definition.weapon();
            return new WeaponDefinition(base.id(), base.schemaVersion(), base.revision(), base.firingMode(),
                    base.baseDamage(), statModifiers, enchantments);
        }
    }

    private final String revision;
    private final Map<String, ItemDefinition> items;
    private final Map<String, EnchantDefinition> enchants;
    private final Map<String, List<StatModifier>> rolls;

    public ItemRegistry(String revision, List<ItemDefinition> items, List<EnchantDefinition> enchants,
                        Map<String, List<StatModifier>> rolls) {
        if (revision == null || !revision.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid registry revision");
        this.revision = revision;
        this.items = items.stream().collect(Collectors.toUnmodifiableMap(item -> item.weapon().id(), Function.identity()));
        this.enchants = enchants.stream().collect(Collectors.toUnmodifiableMap(EnchantDefinition::id, Function.identity()));
        this.rolls = rolls.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> ItemValidationException.id(entry.getKey()), entry -> List.copyOf(entry.getValue())));
        for (ItemDefinition item : this.items.values()) {
            if (!this.rolls.keySet().containsAll(item.allowedRolls())) throw new IllegalArgumentException("Unknown allowed roll");
            var levels = item.weapon().enchantments().stream().collect(Collectors.toMap(WeaponDefinition.Enchantment::id, WeaponDefinition.Enchantment::level));
            var resolved = resolve(new ItemInstance(identity(item, new UUID(0, 0)), revision, levels, List.of()));
            if (!resolved.enchantments().equals(item.weapon().enchantments())) {
                throw new IllegalArgumentException("Definition enchant kinds disagree with trusted registry");
            }
        }
    }

    public String revision() { return revision; }
    public Map<String, ItemDefinition> definitions() { return items; }
    public EnchantDefinition enchant(String id) {
        var definition = enchants.get(ItemValidationException.id(id));
        if (definition == null) throw new ItemValidationException(UNKNOWN_ENCHANT, "Unknown enchant: " + id);
        return definition;
    }

    /** A grant creates a fresh UUID; copying/moving an existing item preserves its UUID. */
    public ItemInstance create(String definitionId) {
        ItemDefinition item = definition(definitionId);
        return resolve(new ItemInstance(identity(item, UUID.randomUUID()), revision,
                item.weapon().enchantments().stream().collect(Collectors.toMap(
                        WeaponDefinition.Enchantment::id, WeaponDefinition.Enchantment::level)), List.of())).instance();
    }

    /** Full replacement, not additive merging with default enchants. Revalidates every edit. */
    public ItemInstance edit(ItemInstance original, Map<String, Integer> levels, List<String> rolledIds) {
        resolve(original);
        return resolve(new ItemInstance(original.identity(), original.registryRevision(), levels, rolledIds)).instance();
    }

    public ResolvedItem resolve(ItemInstance input) {
        WeaponIdentity identity = input.identity();
        if (identity.schemaVersion() != SCHEMA_VERSION) {
            throw new ItemValidationException(UNSUPPORTED_SCHEMA, "Unsupported item schema " + identity.schemaVersion() + "; no migration is defined");
        }
        ItemDefinition item = definition(identity.definitionId());
        if (!revision.equals(input.registryRevision()) || !item.weapon().revision().equals(identity.definitionRevision())) {
            throw new ItemValidationException(REVISION_MISMATCH, "Item/catalog revision mismatch; explicit migration required");
        }
        if (input.enchantLevels().size() > MAX_ENTRIES || input.rolledModifierIds().size() > MAX_ENTRIES) {
            throw new ItemValidationException(MALFORMED_DATA, "Too many enchant or roll entries");
        }
        var levels = new ArrayList<WeaponDefinition.Enchantment>();
        var modifiers = new ArrayList<>(item.weapon().statModifiers());
        int ultimates = 0;
        for (var entry : input.enchantLevels().entrySet()) {
            EnchantDefinition enchant = enchant(entry.getKey());
            var validated = enchant.validate(entry.getValue(), item.weapon().firingMode());
            if (validated.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE && ++ultimates > 1) {
                throw new ItemValidationException(MULTIPLE_ULTIMATES, "A weapon can have only one ultimate enchantment");
            }
            levels.add(validated);
            modifiers.addAll(enchant.levelModifiers().get(validated.level()));
        }
        var seen = new HashSet<String>();
        for (String roll : input.rolledModifierIds()) {
            ItemValidationException.id(roll);
            if (!seen.add(roll)) throw new ItemValidationException(DUPLICATE_ROLL, "Duplicate roll: " + roll);
            if (!item.allowedRolls().contains(roll)) throw new ItemValidationException(UNKNOWN_ROLL, "Roll not allowed on this definition: " + roll);
            modifiers.addAll(rolls.get(roll));
        }
        // baseDamage remains solely on WeaponDefinition; it is NOT also a flat modifier.
        return new ResolvedItem(input, item, WeaponDefinition.validatedEnchantments(levels),
                modifiers.stream().sorted(StatModifier.EXPLANATION_ORDER).toList());
    }

    private ItemDefinition definition(String id) {
        var item = items.get(ItemValidationException.id(id));
        if (item == null) throw new ItemValidationException(UNKNOWN_DEFINITION, "Unknown item definition: " + id);
        if (item.weapon().schemaVersion() != SCHEMA_VERSION) throw new ItemValidationException(UNSUPPORTED_SCHEMA, "Unsupported definition schema");
        return item;
    }

    private WeaponIdentity identity(ItemDefinition item, UUID id) {
        return new WeaponIdentity(id, item.weapon().id(), item.weapon().schemaVersion(), item.weapon().revision());
    }
}
