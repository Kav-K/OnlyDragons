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
    /**
     * Only supported persistent weapon schema; other positive versions reject without automatic migration.
     */
    public static final int SCHEMA_VERSION = 1;
    /**
     * Maximum requested enchant selections and roll selections per instance, each checked separately.
     */
    public static final int MAX_ENTRIES = 32;

    /**
     * Trusted projection when produced by resolve; the public constructor only freezes its lists.
     * @param instance exact validated immutable input identity/selections
     * @param definition retained trusted definition
     * @param enchantments immutable validated ID-sorted descriptors
     * @param statModifiers immutable canonical definition/enchant/roll contributions, excluding base damage
     */
    public record ResolvedItem(ItemInstance instance, ItemDefinition definition,
                               List<WeaponDefinition.Enchantment> enchantments,
                               List<StatModifier> statModifiers) {
        /**
         * Copies both lists; constructing this DTO directly does not perform registry resolution.
         */
        public ResolvedItem {
            enchantments = List.copyOf(enchantments);
            statModifiers = List.copyOf(statModifiers);
        }

        /**
         * Pass this projection once to the stat snapshot factory. It already includes all
         * definition, enchant and roll modifiers; additional sources must be external gear/buffs.
         * <p>
         * Returns a fresh offensive projection preserving identity/mode/base with the complete resolved
         * modifier/enchant lists. No stat resolution or random roll is performed here.
         */
        public WeaponDefinition resolvedWeapon() {
            WeaponDefinition base = definition.weapon();
            return new WeaponDefinition(base.id(), base.schemaVersion(), base.revision(), base.firingMode(),
                    base.baseDamage(), statModifiers, enchantments);
        }
    }

    /**
     * Immutable exact-revision routes; empty in a concrete catalog and populated in an aggregate router.
     */
    private final Map<String, ItemRegistry> catalogs;
    private final String revision;
    /**
     * Immutable trusted definitions keyed by stable ID; resolution still checks the captured revision.
     */
    private final Map<String, ItemDefinition> items;
    /**
     * Immutable descriptors for this catalog or the aggregate inspection view; routed resolution uses the concrete catalog.
     */
    private final Map<String, EnchantDefinition> enchants;
    /**
     * Immutable named roll modifiers; item selections reference these trusted lists rather than supplying numeric effects.
     */
    private final Map<String, List<StatModifier>> rolls;

    /**
     * Builds one concrete immutable catalog and validates every definition default through resolve.
     * Duplicate IDs, unknown allowed rolls, invalid revisions/schemas or mismatched trusted enchant
     * kinds reject before publication. Rolls are named fixed modifier bundles, not random amounts.
     * @param revision 1–64 letters/digits/dot/underscore/hyphen
     * @param items trusted unique item definitions
     * @param enchants trusted unique enchant descriptors
     * @param rolls named immutable-copy modifier bundles referenced by definition allowlists
     */
    public ItemRegistry(String revision, List<ItemDefinition> items, List<EnchantDefinition> enchants,
                        Map<String, List<StatModifier>> rolls) {
        if (revision == null || !revision.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid registry revision");
        this.catalogs = Map.of();
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

    /**
     * Exact revision routing; catalogs must have disjoint item IDs and cannot nest routers.
     * <p>
     * Compatibility wrapper over the ordered flat-catalog constructor; no nested router is admitted.
     */
    public ItemRegistry(ItemRegistry primary, ItemRegistry expanded) {
        this(List.of(primary, expanded));
    }

    /**
     * Flat, ordered catalogs. Later descriptors describe new grants, never old item resolution.
     * <p>
     * Requires a nonempty copied list of concrete catalogs with distinct revisions and disjoint
     * item IDs. Later catalogs replace aggregate enchant descriptors only; resolving an existing
     * instance still uses its exact original catalog. revision() is the first catalog label,
     * not an identity for the aggregate content. All candidate maps are built before publication.
     */
    public ItemRegistry(List<ItemRegistry> concreteCatalogs) {
        if (concreteCatalogs.isEmpty()) throw new IllegalArgumentException("Missing catalogs");
        var routes = new java.util.LinkedHashMap<String, ItemRegistry>();
        var definitions = new java.util.HashMap<String, ItemDefinition>();
        var descriptors = new java.util.HashMap<String, EnchantDefinition>();
        for (var catalog : List.copyOf(concreteCatalogs)) {
            if (!catalog.catalogs.isEmpty() || routes.putIfAbsent(catalog.revision, catalog) != null)
                throw new IllegalArgumentException("Catalog routing requires distinct concrete revisions");
            catalog.items.forEach((id, item) -> {
                if (definitions.putIfAbsent(id, item) != null)
                    throw new IllegalArgumentException("Ambiguous item grant ID: " + id);
            });
            descriptors.putAll(catalog.enchants);
        }
        catalogs = Map.copyOf(routes);
        revision = concreteCatalogs.getFirst().revision;
        items = Map.copyOf(definitions);
        enchants = Map.copyOf(descriptors);
        rolls = Map.of();
    }

    /**
     * Inert catalog bindings must resolve definitions inside their explicitly declared revision.
     * <p>
     * Returns the exact concrete revision or throws REVISION_MISMATCH; no nearest-version fallback.
     * A concrete catalog returns itself only for its own exact label.
     */
    public ItemRegistry catalog(String requestedRevision) {
        if (catalogs.isEmpty() && revision.equals(requestedRevision)) return this;
        var selected = catalogs.get(requestedRevision);
        if (selected == null) throw new ItemValidationException(REVISION_MISMATCH, "Unsupported item catalog revision");
        return selected;
    }

    /**
     * Returns immutable descriptors for inspection; in a router, later catalogs win duplicate
     * enchant IDs. Resolve old item compatibility through its concrete catalog, not this aggregate.
     */
    public Map<String, EnchantDefinition> enchantments() { return enchants; }

    /**
     * Returns the concrete label, or the first concrete label for a router; it is not a content hash.
     */
    public String revision() { return revision; }
    /**
     * Returns immutable grant-ID definitions; a router contains all disjoint catalog definitions.
     * Map iteration order is unspecified.
     */
    public Map<String, ItemDefinition> definitions() { return items; }
    /**
     * Looks up a strict lowercase ID in the descriptor view; malformed IDs and unknown enchants
     * throw typed rejection. This does not establish availability or compatibility for an old item.
     */
    public EnchantDefinition enchant(String id) {
        var definition = enchants.get(ItemValidationException.id(id));
        if (definition == null) throw new ItemValidationException(UNKNOWN_ENCHANT, "Unknown enchant: " + id);
        return definition;
    }

    /**
     * A grant creates a fresh UUID; copying/moving an existing item preserves its UUID.
     * <p>
     * Selects a definition, allocates a new UUID and validates its default enchants with no rolls.
     * Returns only an immutable item value; inventory grant, material encoding and capacity belong
     * to the adapter. Unknown definition/schema/revision problems throw typed validation failures.
     */
    public ItemInstance create(String definitionId) {
        if (!catalogs.isEmpty()) {
            return catalogs.values().stream().filter(catalog -> catalog.items.containsKey(definitionId))
                    .findFirst().orElseThrow(() -> new ItemValidationException(UNKNOWN_DEFINITION,
                            "Unknown item definition: " + definitionId)).create(definitionId);
        }
        ItemDefinition item = definition(definitionId);
        return resolve(new ItemInstance(identity(item, UUID.randomUUID()), revision,
                item.weapon().enchantments().stream().collect(Collectors.toMap(
                        WeaponDefinition.Enchantment::id, WeaponDefinition.Enchantment::level)), List.of())).instance();
    }

    /**
     * Full replacement, not additive merging with default enchants. Revalidates every edit.
     * <p>
     * Validates the original first, then returns a fully validated replacement preserving UUID,
     * definition/schema/revision and catalog. Empty selections remove effects; defaults are not merged.
     * Failure leaves the immutable original unchanged; no inventory or persistence write occurs.
     */
    public ItemInstance edit(ItemInstance original, Map<String, Integer> levels, List<String> rolledIds) {
        resolve(original);
        return resolve(new ItemInstance(original.identity(), original.registryRevision(), levels, rolledIds)).instance();
    }

    /**
     * Converts untrusted immutable selections to trusted content using exact catalog/schema/definition
     * revisions, availability/mode/level checks, one-ultimate enforcement and allowed unique rolls.
     * Definition modifiers, selected enchant bundles and named rolls are sorted canonically. Base
     * damage remains only on the weapon; never add it again as a flat modifier.
     * @param input nonnull immutable instance, which is not mutated
     * @return retained input/definition plus complete trusted immutable projections
     * @throws ItemValidationException for unsupported or malformed content; no migration is attempted
     */
    public ResolvedItem resolve(ItemInstance input) {
        if (!catalogs.isEmpty()) return catalog(input.registryRevision()).resolve(input);
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

    /**
     * Validates strict ID syntax and supported definition schema before returning the trusted item;
     * unknown IDs fail with UNKNOWN_DEFINITION.
     */
    private ItemDefinition definition(String id) {
        var item = items.get(ItemValidationException.id(id));
        if (item == null) throw new ItemValidationException(UNKNOWN_DEFINITION, "Unknown item definition: " + id);
        if (item.weapon().schemaVersion() != SCHEMA_VERSION) throw new ItemValidationException(UNSUPPORTED_SCHEMA, "Unsupported definition schema");
        return item;
    }

    /**
     * Combines an explicitly supplied instance UUID with exact trusted definition references.
     * The registry caller decides whether that UUID is a new grant or a validation sentinel.
     */
    private WeaponIdentity identity(ItemDefinition item, UUID id) {
        return new WeaponIdentity(id, item.weapon().id(), item.weapon().schemaVersion(), item.weapon().revision());
    }
}
