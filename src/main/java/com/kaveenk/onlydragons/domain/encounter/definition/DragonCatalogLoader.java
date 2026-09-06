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
    private final Map<MechanicRevision, CombatProfile> combat;
    private final Map<MechanicRevision, PhaseProfile> phases;

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

    public static DragonCatalogLoader calibration(ItemRegistry items) {
        var combat = CombatProfile.calibration();
        return new DragonCatalogLoader(items, List.of(combat), List.of(new PhaseProfile(
                new MechanicRevision("test-dragon-phase-contract", "v1"), Set.of(combat.mechanic()))));
    }

    public DragonCatalog bundled() {
        try (var dragons = DragonCatalogLoader.class.getResourceAsStream("/encounters/test-dragon-v1.properties");
             var tables = DragonCatalogLoader.class.getResourceAsStream("/encounters/sample-tables-v1.properties")) {
            return load(dragons, tables);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read bundled dragon catalog", failure); }
    }

    public DragonCatalog load(InputStream dragonInput, InputStream tableInput) throws IOException {
        var tableData = new Fields(tableInput);
        var tableCatalog = tableData.identity("");
        String itemCatalog = tableData.get("itemCatalogRevision");
        if (!items.revision().equals(itemCatalog)) throw new IllegalArgumentException("Item catalog revision mismatch");
        var tables = new HashMap<String, DragonCatalog.SampleTable>();
        for (String id : tableData.ids("tables")) {
            String prefix = "table." + id + ".";
            var identity = tableData.identity(prefix);
            if (!id.equals(identity.id())) throw new IllegalArgumentException("Table ID mismatch");
            var bindings = new ArrayList<DragonCatalog.ItemBinding>();
            for (String itemId : tableData.ids(prefix + "items")) {
                var reference = tableData.identity(prefix + "item." + itemId + ".");
                var definition = items.definitions().get(itemId);
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

    private static final class Fields {
        private final Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate property: " + key);
                return super.put(key, value);
            }
        };
        private final Set<String> consumed = new HashSet<>();
        Fields(InputStream input) throws IOException {
            if (input == null) throw new IllegalArgumentException("Missing catalog input");
            values.load(input);
        }
        String get(String key) {
            consumed.add(key);
            String value = values.getProperty(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property: " + key);
            return value;
        }
        DefinitionIdentity identity(String prefix) {
            return new DefinitionIdentity(get(prefix + "id"), Integer.parseInt(get(prefix + "schema")), get(prefix + "revision"));
        }
        MechanicRevision mechanic(String prefix) {
            return new MechanicRevision(DefinitionIdentity.token(get(prefix + "id")), DefinitionIdentity.token(get(prefix + "revision")));
        }
        double number(String key) { return Double.parseDouble(get(key)); }
        List<String> ids(String key) {
            var ids = List.of(get(key).split(",", -1));
            if (ids.size() > 32 || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate/too many IDs: " + key);
            ids.forEach(DefinitionIdentity::token);
            return ids;
        }
        void complete() {
            if (!consumed.equals(values.stringPropertyNames())) throw new IllegalArgumentException("Unexpected catalog properties");
        }
    }
}
