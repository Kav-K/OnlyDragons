package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/** Strict, whole-candidate loader with caller-owned streams and immutable trusted dependencies. */
public final class DragonCatalogLoader {
    private final ItemRegistry items;
    /**
     * Immutable trusted combat policies indexed by exact identity; loading definitions cannot invent a policy.
     */
    private final Map<MechanicRevision, CombatProfile> combat;
    /**
     * Immutable trusted phase policies indexed by exact identity, retained by resolved selections.
     */
    private final Map<MechanicRevision, PhaseProfile> phases;

    /**
     * Captures immutable item/combat/phase catalogs. Rejects empty profile lists, duplicate profile
     * family IDs (even across revisions) and unknown phase compatibility references before use.
     * @param items nonnull exact-revision item catalog/router for inert bindings
     * @param combat nonempty combat policies with distinct profile-family IDs
     * @param phases nonempty phase policies with distinct IDs and known combat compatibility
     * @throws NullPointerException if a required catalog or value is null
     * @throws IllegalArgumentException for empty, duplicate or unresolved profile references
     */
    public DragonCatalogLoader(ItemRegistry items, List<CombatProfile> combat, List<PhaseProfile> phases) {
        this.items = Objects.requireNonNull(items);
        this.combat = index(combat, CombatProfile::mechanic);
        this.phases = index(phases, PhaseProfile::mechanic);
        for (var phase : this.phases.values()) {
            if (!this.combat.keySet().containsAll(phase.compatibleCombatProfiles())) {
                throw new IllegalArgumentException("Unknown compatible combat reference");
            }
        }
    }

    /**
     * Creates the loader for combat-calibration/v1 and the inert test-dragon-phase-contract/v1
     * using the supplied exact item-catalog router; it does not adopt a live registry.
     * @param items exact trusted item catalog/router for sample table bindings
     * @return loader with combat-calibration/v1 and the inert test phase contract
     */
    public static DragonCatalogLoader calibration(ItemRegistry items) {
        var combat = CombatProfile.calibration();
        return new DragonCatalogLoader(items, List.of(combat), List.of(new PhaseProfile(
                new MechanicRevision("test-dragon-phase-contract", "v1"), Set.of(combat.mechanic()))));
    }

    /**
     * Loads both bundled properties resources and closes both streams. Missing inputs reject
     * with IllegalArgumentException; I/O failure is wrapped in IllegalStateException. Returns a
     * complete immutable candidate without modifying a registry.
     * @return complete validated immutable bundled catalog candidate, without registry adoption
     * @throws IllegalArgumentException if a bundled resource is missing or content is invalid
     * @throws IllegalStateException if reading a bundled resource fails
     */
    public DragonCatalog bundled() {
        try (var dragons = DragonCatalogLoader.class.getResourceAsStream("/encounters/test-dragon-v1.properties");
             var tables = DragonCatalogLoader.class.getResourceAsStream("/encounters/sample-tables-v1.properties")) {
            return load(dragons, tables);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read bundled dragon catalog", failure); }
    }

    /**
     * Parses the table catalog first, resolves exact item references, then validates dragon/type,
     * combat/phase and table references. Duplicate/missing/unknown properties, ID-list errors,
     * stale references and incompatible numeric policies reject the entire candidate.
     * @param dragonInput caller-owned nonnull Java-properties stream, left open
     * @param tableInput caller-owned nonnull table stream, left open
     * @return full immutable resolved candidate, with no partial adoption
     * @throws IOException for stream read failure
     * @throws IllegalArgumentException for malformed or incompatible content
     */
    public DragonCatalog load(InputStream dragonInput, InputStream tableInput) throws IOException {
        var tableData = new Fields(tableInput);
        var tableCatalog = tableData.identity("");
        String itemCatalog = tableData.get("itemCatalogRevision");
        var selectedItems = items.catalog(itemCatalog);
        var tables = new HashMap<String, DragonCatalog.SampleTable>();
        for (String id : tableData.ids("tables")) {
            String prefix = "table." + id + ".";
            var identity = tableData.identity(prefix);
            if (!id.equals(identity.id())) throw new IllegalArgumentException("Table ID mismatch");
            var bindings = new ArrayList<DragonCatalog.ItemBinding>();
            for (String itemId : tableData.ids(prefix + "items")) {
                var reference = tableData.identity(prefix + "item." + itemId + ".");
                var definition = selectedItems.definitions().get(itemId);
                if (!itemId.equals(reference.id()) || definition == null) throw new IllegalArgumentException("Unknown/mismatched item reference");
                bindings.add(new DragonCatalog.ItemBinding(itemCatalog, reference, definition));
            }
            tables.put(id, new DragonCatalog.SampleTable(tableCatalog, identity, bindings));
        }
        tableData.complete();
        var data = new Fields(dragonInput);
        var catalog = data.identity("");
        var definitions = new HashMap<String, DragonCatalog.Selection>();
        for (String id : data.ids("types")) {
            String prefix = "type." + id + ".";
            var identity = data.identity(prefix);
            if (!id.equals(identity.id())) throw new IllegalArgumentException("Type ID mismatch");
            var tableRef = data.identity(prefix + "table.");
            var table = tables.get(tableRef.id());
            if (table == null || !table.identity().equals(tableRef)) throw new IllegalArgumentException("Unknown/stale table reference");
            var combatProfile = combat.get(data.mechanic(prefix + "combat."));
            var phaseProfile = phases.get(data.mechanic(prefix + "phase."));
            if (combatProfile == null || phaseProfile == null) throw new IllegalArgumentException("Unknown/stale combat or phase reference");
            definitions.put(id, new DragonCatalog.Selection(catalog, identity, data.get(prefix + "displayName"),
                    data.number(prefix + "maxHealth"), data.number(prefix + "defense"), combatProfile, phaseProfile, table));
        }
        data.complete();
        return new DragonCatalog(catalog, definitions, tables);
    }

    /**
     * Builds an immutable exact-revision lookup while rejecting duplicate profile family IDs;
     * multiple versions of one family are not implicitly selected by order.
     */
    private static <T> Map<MechanicRevision, T> index(List<T> values, Function<T, MechanicRevision> identity) {
        var result = new HashMap<MechanicRevision, T>();
        var ids = new HashSet<String>();
        for (T value : values) {
            var key = identity.apply(Objects.requireNonNull(value));
            DefinitionIdentity.token(key.profileId());
            DefinitionIdentity.token(key.revision());
            if (!ids.add(key.profileId())) throw new IllegalArgumentException("Duplicate profile ID: " + key.profileId());
            result.put(key, value);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Missing profiles");
        return Map.copyOf(result);
    }

    /**
     * Per-input strict parser and consumed-field ledger. All expected fields must be explicitly
     * read and complete() must match the full property vocabulary; duplicates reject during load.
     */
    private static final class Fields {
        private final Properties values = new Properties() {
            /**
             * Rejects a repeated property before replacement, preserving evidence that Properties.load would otherwise overwrite.
             * Returns the superclass insertion result for a new key; this parser is not a general concurrent store.
             */
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate property: " + key);
                return super.put(key, value);
            }
        };
        /**
         * Fields explicitly read by this parser, compared with the complete input key set to reject unknown properties.
         */
        private final Set<String> consumed = new HashSet<>();
        /**
         * Loads one required caller-owned stream without closing it; rejects null or duplicate fields.
         */
        Fields(InputStream input) throws IOException {
            if (input == null) throw new IllegalArgumentException("Missing catalog input");
            values.load(input);
        }
        /**
         * Marks the field consumed and returns its original nonblank value, without trimming; missing
         * or blank properties reject instead of defaulting.
         */
        String get(String key) {
            consumed.add(key);
            String value = values.getProperty(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property: " + key);
            return value;
        }
        /**
         * Consumes the id/schema/revision triple at a prefix and validates the supported reference identity.
         */
        DefinitionIdentity identity(String prefix) {
            return new DefinitionIdentity(get(prefix + "id"), Integer.parseInt(get(prefix + "schema")), get(prefix + "revision"));
        }
        /**
         * Consumes and validates bounded id/revision tokens for an exact trusted mechanic lookup.
         */
        MechanicRevision mechanic(String prefix) {
            return new MechanicRevision(DefinitionIdentity.token(get(prefix + "id")), DefinitionIdentity.token(get(prefix + "revision")));
        }
        /**
         * Parses a required numeric field; the consuming Selection enforces finite HP/defense bounds.
         */
        double number(String key) { return Double.parseDouble(get(key)); }
        /**
         * Parses comma-separated exact tokens, preserving order and rejecting empty/whitespace tokens,
         * duplicates or more than 32 entries. No whitespace trimming or implicit default occurs.
         */
        List<String> ids(String key) {
            var ids = List.of(get(key).split(",", -1));
            if (ids.size() > 32 || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate/too many IDs: " + key);
            ids.forEach(DefinitionIdentity::token);
            return ids;
        }
        /**
         * Rejects any unread property; call only after all expected dynamic entries have been consumed.
         */
        void complete() {
            if (!consumed.equals(values.stringPropertyNames())) throw new IllegalArgumentException("Unexpected catalog properties");
        }
    }
}
