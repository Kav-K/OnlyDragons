package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DragonCatalogTest {
    private final DragonCatalogLoader loader = DragonCatalogLoader.calibration(CalibrationLoadouts.registry());
    private final DragonDefinitionRegistry registry = new DragonDefinitionRegistry(loader);
    private final String dragons = resource("test-dragon-v1.properties");
    private final String tables = resource("sample-tables-v1.properties");

    @Test void shipsExactlyOneResolvedTestType() {
        var catalog = registry.snapshot();
        assertEquals(Set.of("test_dragon"), catalog.definitions().keySet());
        assertEquals(Set.of("test_dragon_sample"), catalog.tables().keySet());
        var selected = catalog.select("test_dragon");
        assertEquals(new DefinitionIdentity("test_dragon", 1, "v1"), selected.identity());
        assertEquals("Test Dragon (Calibration)", selected.displayName());
        assertEquals(1000, selected.maxHealth());
        assertEquals(0, selected.defense());
        assertEquals(CombatProfile.calibration(), selected.combatProfile());
        assertTrue(selected.phaseProfile().compatibleCombatProfiles().contains(selected.combatProfile().mechanic()));
        var item = selected.table().items().getFirst();
        assertEquals("ordinary", item.identity().id());
        assertEquals(CalibrationLoadouts.REVISION, item.catalogRevision());
        assertEquals(CalibrationLoadouts.registry().definitions().get("ordinary"), item.definition());
        assertThrows(IllegalArgumentException.class, () -> catalog.select("missing"));
    }

    @ParameterizedTest @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "-1", "1000000001", "1e309", "bad"})
    void rejectsInvalidHpAndDefenseWithWholeCandidateRollback(String value) {
        rejects(dragons.replace("maxHealth=1000", "maxHealth=" + value), tables);
        rejects(dragons.replace("defense=0", "defense=" + value), tables);
    }

    @Test void validatesNumericBoundariesWithoutClamping() throws Exception {
        rejects(dragons.replace("maxHealth=1000", "maxHealth=0"), tables);
        rejects(dragons.replace("maxHealth=1000", "maxHealth=-0.0"), tables);
        var maximum = loader.load(stream(dragons.replace("maxHealth=1000", "maxHealth=1000000000")
                .replace("defense=0", "defense=1000000000")), stream(tables)).select("test_dragon");
        assertEquals(1e9, maximum.maxHealth());
        assertEquals(1e9, maximum.defense());
        assertEquals(Double.MIN_VALUE, loader.load(stream(dragons.replace("maxHealth=1000", "maxHealth=4.9e-324")),
                stream(tables)).select("test_dragon").maxHealth());
    }

    @Test void everyRequiredFieldIsRequiredAndUnknownFieldsReject() {
        for (String line : dragons.lines().filter(s -> s.contains("=")).toList()) rejects(dragons.replace(line + "\n", ""), tables);
        for (String line : tables.lines().filter(s -> s.contains("=")).toList()) rejects(dragons, tables.replace(line + "\n", ""));
        rejects(dragons + "unknown=1\n", tables);
        for (String field : List.of("rewardsEnabled=true", "chance=1", "quantity=1", "xp=10", "currency=10", "rank=1")) {
            rejects(dragons, tables + field + "\n");
        }
    }

    @Test void duplicateIdsPropertiesAndIncompleteSecondEntriesReject() {
        rejects(dragons.replace("types=test_dragon", "types=test_dragon,test_dragon"), tables);
        rejects(dragons, tables.replace("tables=test_dragon_sample", "tables=test_dragon_sample,test_dragon_sample"));
        rejects(dragons, tables.replace("items=ordinary", "items=ordinary,ordinary"));
        rejects(dragons + "type.test_dragon.maxHealth=200000\n", tables);
        rejects(dragons, tables + "table.test_dragon_sample.revision=v2\n");
        rejects(dragons.replace("types=test_dragon", "types=test_dragon,second"), tables);
        rejects(dragons, tables.replace("tables=test_dragon_sample", "tables=test_dragon_sample,second"));
        rejects(dragons.replace("types=test_dragon", "types="), tables);
        rejects(dragons.replace("types=test_dragon", "types=test_dragon,"), tables);
    }

    @Test void unknownMismatchedAndStaleReferencesReject() {
        for (String field : List.of("type.test_dragon.id", "type.test_dragon.combat.id", "type.test_dragon.phase.id", "type.test_dragon.table.id")) {
            rejects(set(dragons, field, "unknown"), tables);
            rejects(set(dragons, field, ""), tables);
        }
        for (String field : List.of("type.test_dragon.combat.revision", "type.test_dragon.phase.revision", "type.test_dragon.table.revision")) {
            rejects(set(dragons, field, "v999"), tables);
        }
        rejects(dragons, tables.replace("ordinary", "nonexistent"));
        rejects(dragons, set(tables, "table.test_dragon_sample.id", "mismatch"));
        rejects(dragons, set(tables, "table.test_dragon_sample.item.ordinary.id", "crit"));
        rejects(dragons, set(tables, "itemCatalogRevision", "stale"));
        rejects(dragons, set(tables, "table.test_dragon_sample.item.ordinary.revision", "stale"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "2", "-1", "NaN"})
    void rejectsUnsupportedSchemasAtAllBoundaries(String schema) {
        for (String field : List.of("schema", "type.test_dragon.schema", "type.test_dragon.table.schema")) rejects(set(dragons, field, schema), tables);
        for (String field : List.of("schema", "table.test_dragon_sample.schema", "table.test_dragon_sample.item.ordinary.schema")) rejects(dragons, set(tables, field, schema));
    }

    @Test void trustedProfilesAreCopiedAndCompatibilityIsEnforced() throws Exception {
        var base = CombatProfile.calibration();
        var other = CombatProfile.dragonExperiment(new MechanicRevision("other", "v1"), .25);
        var allowed = new HashSet<>(Set.of(other.mechanic()));
        var phase = new PhaseProfile(new MechanicRevision("test-dragon-phase-contract", "v1"), allowed);
        var profiles = new ArrayList<>(List.of(base, other));
        var candidateLoader = new DragonCatalogLoader(CalibrationLoadouts.registry(), profiles, List.of(phase));
        allowed.clear();
        profiles.clear();
        assertThrows(IllegalArgumentException.class, () -> candidateLoader.load(stream(dragons), stream(tables)));
        var compatible = candidateLoader.load(stream(set(dragons, "type.test_dragon.combat.id", "other")), stream(tables));
        assertEquals(.25, compatible.select("test_dragon").combatProfile().ferocityHealthFraction());
        assertThrows(IllegalArgumentException.class, () -> new DragonCatalogLoader(CalibrationLoadouts.registry(), List.of(base, base), List.of(phase)));
        assertThrows(IllegalArgumentException.class, () -> new DragonCatalogLoader(CalibrationLoadouts.registry(), List.of(base, other), List.of(phase, phase)));
        assertThrows(IllegalArgumentException.class, () -> new DragonCatalogLoader(CalibrationLoadouts.registry(), List.of(base), List.of(phase)));
    }

    @Test void distinctCalibrationTypesAndTablesDoNotLeakAndOldSelectionSurvivesReplacement() throws Exception {
        var retainedCatalog = registry.snapshot();
        var retained = retainedCatalog.select("test_dragon");
        String nextDragons = set(dragons, "revision", "v2").replace("types=test_dragon", "types=test_dragon,second")
                + dragons.lines().filter(s -> s.startsWith("type.")).map(s -> s.replace("type.test_dragon.", "type.second.")).reduce("", (a,b) -> a + b + "\n");
        nextDragons = set(nextDragons, "type.second.id", "second");
        nextDragons = set(nextDragons, "type.second.maxHealth", "250000");
        nextDragons = set(nextDragons, "type.second.table.id", "second_sample");
        String nextTables = set(tables, "revision", "v2").replace("tables=test_dragon_sample", "tables=test_dragon_sample,second_sample")
                + tables.lines().filter(s -> s.startsWith("table.")).map(s -> s.replace("test_dragon_sample", "second_sample")
                .replace("ordinary", "crit")).reduce("", (a,b) -> a + b + "\n");
        var next = registry.replace(stream(nextDragons), stream(nextTables));
        assertSame(next, registry.snapshot());
        assertEquals(2, next.definitions().size());
        assertEquals(250000, next.select("second").maxHealth());
        assertEquals("crit", next.select("second").table().items().getFirst().identity().id());
        assertEquals("ordinary", next.select("test_dragon").table().items().getFirst().identity().id());
        assertEquals("v1", retained.catalogIdentity().revision());
        assertEquals("v1", retained.table().catalogIdentity().revision());
        assertEquals(Set.of("test_dragon"), retainedCatalog.definitions().keySet());
        assertThrows(UnsupportedOperationException.class, () -> retainedCatalog.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> retainedCatalog.tables().clear());
        assertThrows(UnsupportedOperationException.class, () -> retained.table().items().clear());
        assertThrows(UnsupportedOperationException.class, () -> retained.phaseProfile().compatibleCombatProfiles().clear());
        rejects(set(nextDragons, "type.second.defense", "NaN"), nextTables);
        rejects(nextDragons, set(nextTables, "table.second_sample.item.crit.revision", "stale"));
        assertEquals("crit", registry.snapshot().select("second").table().items().getFirst().identity().id());
    }

    @Test void retainsDefinitionAndTableRevisionsAfterContentReplacement() throws Exception {
        var old = registry.snapshot().select("test_dragon");
        var next = registry.replace(stream(set(dragons.replace("revision=v1", "revision=v2"), "type.test_dragon.combat.revision", "v1")
                .replace("phase.revision=v2", "phase.revision=v1").replace("maxHealth=1000", "maxHealth=200000")),
                stream(tables.replace("revision=v1", "revision=v2").replace("ordinary", "crit"))).select("test_dragon");
        assertEquals("v2", next.identity().revision());
        assertEquals("v2", next.table().identity().revision());
        assertEquals(200000, next.maxHealth());
        assertEquals(1000, old.maxHealth());
        assertEquals("v1", old.identity().revision());
        assertEquals("ordinary", old.table().items().getFirst().identity().id());
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "bad/id", "bad:id", "bad id"})
    void invalidIdentityTokensReject(String token) {
        rejects(set(dragons, "id", token), tables);
        rejects(set(dragons, "type.test_dragon.revision", token), tables);
        rejects(dragons, set(tables, "table.test_dragon_sample.revision", token));
    }

    @Test void ioFailureAndAbsentStreamsDoNotAdoptPartialCatalog() {
        var before = registry.snapshot();
        var broken = new InputStream() { @Override public int read() throws IOException { throw new IOException("fixture failure"); } };
        assertThrows(IOException.class, () -> registry.replace(broken, stream(tables)));
        assertSame(before, registry.snapshot());
        assertThrows(IllegalArgumentException.class, () -> registry.replace(stream(dragons), null));
        assertSame(before, registry.snapshot());
    }

    private void rejects(String candidate, String candidateTables) {
        var before = registry.snapshot();
        assertThrows(IllegalArgumentException.class, () -> registry.replace(stream(candidate), stream(candidateTables)));
        assertSame(before, registry.snapshot(), "Every rejection must retain the entire previous catalog");
    }
    private static String set(String text, String key, String value) {
        return text.replaceAll("(?m)^" + java.util.regex.Pattern.quote(key) + "=.*$", key + "=" + value);
    }
    private static InputStream stream(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1)); }
    private static String resource(String name) {
        try (var input = DragonCatalogTest.class.getResourceAsStream("/encounters/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
}
