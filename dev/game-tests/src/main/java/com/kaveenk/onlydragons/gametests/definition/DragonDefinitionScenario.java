package com.kaveenk.onlydragons.gametests.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.encounter.definition.*;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Exercises the production classloader, loader and atomic definition registry on Paper.
 * Malformed and alternate catalogs are explicit fixture candidates; the shipped type
 * and full retained selections remain the provenance authority. Native inventory,
 * entity, task and listener sentinels check that catalog operations stay inert.
 * No live fight, reward eligibility, roll or delivery is established by this scenario.
 * Cleanup restores the original bundled registry even after the intentional abort.
 */
public final class DragonDefinitionScenario implements Scenario {
    private final boolean abort;
    /** Creates the positive catalog-validation variant. */
    public DragonDefinitionScenario() { this(false); }
    /**
     * Selects the deliberate cleanup control or the positive validation sequence.
     * @param abort true to replace the registry, then abort so cleanup must restore it
     */
    public DragonDefinitionScenario(boolean abort) { this.abort = abort; }
    /**
     * Checks schema/reference/number admission, atomic rollback and retained full-selection identity.
     * Registers restoration before any replacement; equal revision labels are explicitly
     * allowed to identify different full content, so provenance comparisons retain values.
     * @param context server-thread fixture context and cleanup registry
     * @throws Exception if resources, candidate I/O or fixture initialization fails
     */
    @Override public void start(ScenarioContext context) throws Exception {
        context.mechanicRevision(abort ? "dragon-definition-cleanup-v1" : "dragon-definition-calibration-v2");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("loader_from_production", true,
                DragonCatalogLoader.class.getClassLoader() == context.production().getClass().getClassLoader());
        var registry = context.production().dragonDefinitions();
        var original = registry.snapshot();
        var retained = original.select("test_dragon");
        String dragons = resource("test-dragon-v1.properties");
        String tables = resource("sample-tables-v1.properties");
        // Register before ANY candidate replacement, including rejection probes.
        context.cleanup("production-dragon-catalog", () -> {
            try {
                var restored = registry.replace(stream(dragons), stream(tables));
                boolean matches = restored.identity().equals(original.identity())
                        && restored.definitions().equals(original.definitions()) && restored.tables().equals(original.tables());
                context.check("bundled_catalog_restored", true, matches);
                if (!matches) throw new IllegalStateException("Original production catalog was not restored");
            } catch (IOException failure) {
                context.observe("catalog_restore_failure", failure.toString());
                throw new java.io.UncheckedIOException("Could not restore original production catalog", failure);
            }
        });
        context.check("one_shipped_type", List.of("test_dragon"), original.definitions().keySet().stream().sorted().toList());
        context.check("shipped_calibration", List.of(1000.0, 0.0, "v1", 1),
                List.of(retained.maxHealth(), retained.defense(), retained.identity().revision(), retained.identity().schemaVersion()));
        context.check("explicit_profiles", true, retained.combatProfile().equals(CombatProfile.calibration())
                && retained.phaseProfile().compatibleCombatProfiles().contains(retained.combatProfile().mechanic()));
        context.check("resolved_item_provenance", true, retained.table().items().getFirst().catalogRevision().equals(CalibrationLoadouts.REVISION)
                && retained.table().items().getFirst().definition().equals(CalibrationLoadouts.registry().definitions().get("ordinary")));

        if (abort) {
            registry.replace(stream(set(dragons, "type.test_dragon.maxHealth", "2000")), stream(tables));
            context.check("catalog_changed_before_abort", true, registry.snapshot() != original
                    && registry.snapshot().select("test_dragon").maxHealth() == 2000
                    && original.select("test_dragon").maxHealth() == 1000);
            context.abort();
            return;
        }

        // Independent real Paper state sentinels. The catalog is not given mutation capabilities.
        var inventory = Bukkit.createInventory(null, 9);
        inventory.setItem(3, new ItemStack(Material.BOW));
        byte[] inventoryBefore = inventory.getItem(3).serializeAsBytes();
        var entitiesBefore = entities();
        int tasksBefore = Bukkit.getScheduler().getPendingTasks().size();
        int listenersBefore = HandlerList.getRegisteredListeners(context.production()).size();
        boolean missing = true;
        for (String line : dragons.lines().filter(s -> s.contains("=")).toList()) missing &= rejects(registry, withoutLine(dragons, line), tables);
        for (String line : tables.lines().filter(s -> s.contains("=")).toList()) missing &= rejects(registry, dragons, withoutLine(tables, line));
        context.check("all_required_fields_rejected_atomically", true, missing);
        context.check("duplicate_ids_and_properties", true,
                rejects(registry, dragons.replace("types=test_dragon", "types=test_dragon,test_dragon"), tables)
                && rejects(registry, dragons, tables.replace("tables=test_dragon_sample", "tables=test_dragon_sample,test_dragon_sample"))
                && rejects(registry, dragons, tables.replace("items=ordinary", "items=ordinary,ordinary"))
                && rejects(registry, dragons + "type.test_dragon.maxHealth=1\n", tables)
                && rejects(registry, dragons, tables + "table.test_dragon_sample.revision=v2\n"));
        context.check("unknown_stale_references", true,
                rejects(registry, set(dragons, "type.test_dragon.table.id", "missing"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.table.revision", "v99"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.combat.id", "missing"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.phase.id", "missing"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.combat.revision", "v99"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.phase.revision", "v99"), tables)
                && rejects(registry, dragons, tables.replace("ordinary", "missing"))
                && rejects(registry, dragons, set(tables, "itemCatalogRevision", "stale"))
                && rejects(registry, dragons, set(tables, "table.test_dragon_sample.item.ordinary.revision", "stale")));
        boolean invalidNumbers = true;
        for (String number : List.of("NaN", "Infinity", "-Infinity", "-1", "1000000001", "1e309")) {
            invalidNumbers &= rejects(registry, set(dragons, "type.test_dragon.maxHealth", number), tables);
            invalidNumbers &= rejects(registry, set(dragons, "type.test_dragon.defense", number), tables);
        }
        context.check("nonfinite_out_of_range_rejected", true, invalidNumbers && rejects(registry, set(dragons, "type.test_dragon.maxHealth", "0"), tables));
        var loader = DragonCatalogLoader.calibration(CalibrationLoadouts.registry());
        var maximum = loader.load(stream(set(set(dragons, "type.test_dragon.maxHealth", "1000000000"), "type.test_dragon.defense", "1000000000")), stream(tables));
        context.check("inclusive_numeric_boundaries", List.of(1e9, 1e9), List.of(maximum.select("test_dragon").maxHealth(), maximum.select("test_dragon").defense()));
        context.check("unsupported_schemas", true, rejects(registry, set(dragons, "schema", "2"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.schema", "0"), tables)
                && rejects(registry, set(dragons, "type.test_dragon.table.schema", "2"), tables)
                && rejects(registry, dragons, set(tables, "table.test_dragon_sample.item.ordinary.schema", "2")));
        var alternateCombat = CombatProfile.dragonExperiment(new MechanicRevision("alternate", "v1"), .25);
        var incompatible = new DragonCatalogLoader(CalibrationLoadouts.registry(), List.of(CombatProfile.calibration(), alternateCombat),
                List.of(new PhaseProfile(new MechanicRevision("test-dragon-phase-contract", "v1"), Set.of(alternateCombat.mechanic()))));
        boolean rejected = false;
        try { incompatible.load(stream(dragons), stream(tables)); } catch (IllegalArgumentException expected) { rejected = true; }
        context.check("incompatible_profiles_rejected", true, rejected);
        boolean sideEffectsRejected = true;
        for (String field : List.of("rewardsEnabled=true", "chance=1", "quantity=1", "xp=10", "currency=10", "rank=1")) {
            sideEffectsRejected &= rejects(registry, dragons, tables + field + "\n");
        }
        context.check("reward_fields_fail_closed", true, sideEffectsRejected);

        // Distinct types and tables exist only in this companion's candidates.
        String secondDragon = dragons.lines().filter(s -> s.startsWith("type.")).map(s -> s.replace("type.test_dragon.", "type.second."))
                .reduce("", (a,b) -> a + b + "\n");
        String nextDragons = set(dragons, "revision", "v2").replace("types=test_dragon", "types=test_dragon,second") + secondDragon;
        nextDragons = set(set(set(nextDragons, "type.second.id", "second"), "type.second.maxHealth", "250000"), "type.second.table.id", "second_sample");
        String secondTable = tables.lines().filter(s -> s.startsWith("table.")).map(s -> s.replace("test_dragon_sample", "second_sample").replace("ordinary", "crit"))
                .reduce("", (a,b) -> a + b + "\n");
        String nextTables = set(tables, "revision", "v2").replace("tables=test_dragon_sample", "tables=test_dragon_sample,second_sample") + secondTable;
        var next = registry.replace(stream(nextDragons), stream(nextTables));
        context.check("distinct_type_table_isolation", List.of(1000.0, "ordinary", 250000.0, "crit"),
                List.of(next.select("test_dragon").maxHealth(), next.select("test_dragon").table().items().getFirst().identity().id(),
                        next.select("second").maxHealth(), next.select("second").table().items().getFirst().identity().id()));
        context.check("whole_candidate_rollback_after_adoption", true,
                rejects(registry, set(nextDragons, "type.second.defense", "NaN"), nextTables)
                && rejects(registry, nextDragons, set(nextTables, "table.second_sample.item.crit.revision", "stale"))
                && registry.snapshot() == next);
        var revised = registry.replace(stream(dragons.replace("revision=v1", "revision=v3")
                .replace("combat.revision=v3", "combat.revision=v1").replace("phase.revision=v3", "phase.revision=v1")
                .replace("maxHealth=1000", "maxHealth=200000")), stream(tables.replace("revision=v1", "revision=v3").replace("ordinary", "crit")));
        context.check("retained_definition_table_revisions", List.of("v1", "v1", "v1", "v1", 1000.0, "ordinary", "v3", "v3", 200000.0, "crit"),
                List.of(retained.catalogIdentity().revision(), retained.identity().revision(), retained.table().catalogIdentity().revision(), retained.table().identity().revision(),
                        retained.maxHealth(), retained.table().items().getFirst().identity().id(), revised.select("test_dragon").identity().revision(),
                        revised.select("test_dragon").table().identity().revision(), revised.select("test_dragon").maxHealth(), revised.select("test_dragon").table().items().getFirst().identity().id()));
        boolean immutable = false;
        try { retained.table().items().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        context.check("immutable_selection", true, immutable && original.definitions().size() == 1 && original.select("test_dragon") == retained);
        context.check("io_failure_rollback", true, ioFailure(registry, tables));
        var sameLabels = registry.replace(stream(set(dragons, "type.test_dragon.maxHealth", "2000")), stream(tables)).select("test_dragon");
        context.check("same_labels_distinct_resolved_content", true, sameLabels.identity().equals(retained.identity())
                && sameLabels.catalogIdentity().equals(retained.catalogIdentity()) && !sameLabels.equals(retained)
                && sameLabels.maxHealth() == 2000 && retained.maxHealth() == 1000);
        context.check("no_inventory_mutation", true, inventory.getContents().length == 9 && inventory.getItem(3).getAmount() == 1
                && Arrays.equals(inventoryBefore, inventory.getItem(3).serializeAsBytes())
                && Arrays.stream(inventory.getContents()).filter(java.util.Objects::nonNull).count() == 1);
        context.check("no_entities_items_or_xp_spawned", entitiesBefore, entities());
        context.check("no_production_tasks_or_listeners", List.of(tasksBefore, listenersBefore),
                List.of(Bukkit.getScheduler().getPendingTasks().size(), HandlerList.getRegisteredListeners(context.production()).size()));
        context.observe("scope", "Production bootstrap/loader/registry, synthetic candidates and standalone Paper inventory sentinel. No players, live spawning, native death, fight completion, eligibility, rolls, grants or currency service.");
        context.finish();
    }

    /** Snapshots sorted native entity UUIDs across worlds as an independent no-spawn sentinel. */
    private static List<String> entities() {
        return Bukkit.getWorlds().stream().flatMap(w -> w.getEntities().stream()).map(e -> e.getUniqueId().toString()).sorted().toList();
    }
    /** Requires an invalid candidate to throw and retain the identical previously adopted registry snapshot. */
    private static boolean rejects(DragonDefinitionRegistry registry, String dragons, String tables) throws IOException {
        var before = registry.snapshot();
        try { registry.replace(stream(dragons), stream(tables)); return false; }
        catch (IllegalArgumentException expected) { return before == registry.snapshot(); }
    }
    /** Injects a failing input stream and verifies atomic rollback preserves the exact prior snapshot. */
    private static boolean ioFailure(DragonDefinitionRegistry registry, String tables) {
        var before = registry.snapshot();
        try {
            registry.replace(new InputStream() { /** Injects a deterministic read failure before any candidate adoption. */ @Override public int read() throws IOException { throw new IOException("fixture"); } }, stream(tables));
            return false;
        } catch (IOException expected) { return before == registry.snapshot(); }
    }
    /** Deletes one exact property line using line-separator-independent parsing to test required fields. */
    private static String withoutLine(String text, String removed) {
        return text.lines().filter(line -> !line.equals(removed)).collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    }
    /** Replaces one named fixture property while quoting the key so dots are not treated as regex wildcards. */
    private static String set(String text, String key, String value) {
        return text.replaceAll("(?m)^" + java.util.regex.Pattern.quote(key) + "=.*$", key + "=" + value);
    }
    /** Encodes synthetic Java-properties content with the ISO-8859-1 representation used by this loader fixture. */
    private static InputStream stream(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)); }
    /** Reads bundled catalog bytes through the production loader classloader rather than a companion copy. */
    private static String resource(String name) throws IOException {
        try (var input = DragonCatalogLoader.class.getResourceAsStream("/encounters/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
