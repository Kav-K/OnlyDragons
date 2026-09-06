package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.domain.item.ItemValidationException.Code.*;

class ItemRegistryTest {
    private final ItemRegistry registry = CalibrationLoadouts.registry();

    @Test void grantsHaveDistinctIdentityAndBaseDamageIsNotAddedTwice() {
        var first = registry.create("ordinary");
        var second = registry.create("ordinary");
        assertNotEquals(first.identity().instanceId(), second.identity().instanceId());
        var resolved = registry.resolve(first);
        assertEquals(100, resolved.definition().weapon().baseDamage());
        assertTrue(resolved.statModifiers().stream().noneMatch(m -> m.key() == StatKey.WEAPON_DAMAGE));
        assertEquals(first.identity().definitionId(), second.identity().definitionId());
    }

    @Test void calibrationContributionsAreExplicitAndEffectsRemainData() {
        assertEquals(9, registry.definitions().size());
        assertEquals(WeaponDefinition.FiringMode.SHORTBOW, registry.definitions().get("shortbow_v1").weapon().firingMode());
        for (var row : Map.of("ordinary", 0.0, "crit", 0.0, "ferocity_25", 25.0, "ferocity_100", 100.0,
                "ferocity_500", 500.0, "tracer", 0.0, "duplex", 0.0, "fatal_tempo", 25.0).entrySet()) {
            var item = registry.resolve(registry.create(row.getKey()));
            assertEquals(row.getValue(), item.statModifiers().stream().filter(m -> m.key() == StatKey.FEROCITY).mapToDouble(StatModifier::amount).sum());
            assertEquals(row.getKey().equals("crit") ? 100 : 0,
                    item.statModifiers().stream().filter(m -> m.key() == StatKey.CRIT_CHANCE).mapToDouble(StatModifier::amount).sum());
            assertTrue(item.statModifiers().stream().noneMatch(m -> m.key() == StatKey.CRIT_DAMAGE),
                    "T01 supplies baseline crit damage 50; items must not duplicate it");
        }
        var vicious = registry.edit(registry.create("ordinary"), Map.of("vicious", 5), List.of());
        assertTrue(registry.resolve(vicious).statModifiers().contains(
                new StatModifier("enchant:vicious", StatKey.FEROCITY, ModifierOperation.FLAT, 5, 0)));
    }

    @Test void editsReplaceDefaultsAndUseTrustedUltimateKinds() {
        var original = registry.create("duplex");
        var swapped = registry.edit(original, Map.of("fatal_tempo", 5, "dragon_tracer", 3), List.of());
        assertEquals(original.identity(), swapped.identity());
        assertEquals(Map.of("duplex", 5), original.enchantLevels());
        var result = registry.resolve(swapped);
        assertEquals(1, result.enchantments().stream().filter(e -> e.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE).count());
        assertEquals(MULTIPLE_ULTIMATES, assertThrows(ItemValidationException.class,
                () -> registry.edit(original, Map.of("duplex", 1, "fatal_tempo", 1), List.of())).code());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 6, 255, Integer.MAX_VALUE})
    void unsupportedLevelsReject(int level) {
        assertEquals(INVALID_LEVEL, assertThrows(ItemValidationException.class,
                () -> registry.edit(registry.create("ordinary"), Map.of("duplex", level), List.of())).code());
    }

    @Test void everyRegisteredLevelIsExplicitAndBoundsReject() {
        for (var entry : Map.of("dragon_tracer", 5, "duplex", 5, "fatal_tempo", 5, "vicious", 5, "power", 7, "snipe", 4).entrySet()) {
            for (int level = 1; level <= entry.getValue(); level++) {
                assertEquals(level, registry.enchant(entry.getKey()).validate(level, WeaponDefinition.FiringMode.DRAWN_BOW).level());
            }
            assertThrows(ItemValidationException.class, () -> registry.enchant(entry.getKey()).validate(entry.getValue() + 1, WeaponDefinition.FiringMode.DRAWN_BOW));
        }
        assertEquals(UNKNOWN_ENCHANT, assertThrows(ItemValidationException.class, () -> registry.enchant("overload")).code());
    }

    @Test void unknownDefinitionsRevisionsSchemasAndRollsRejectExplicitly() {
        assertEquals(UNKNOWN_DEFINITION, assertThrows(ItemValidationException.class, () -> registry.create("fake")).code());
        var item = registry.create("ordinary");
        assertEquals(REVISION_MISMATCH, assertThrows(ItemValidationException.class,
                () -> registry.resolve(new ItemInstance(item.identity(), "old", Map.of(), List.of()))).code());
        var identity = item.identity();
        assertEquals(REVISION_MISMATCH, assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(
                new WeaponIdentity(identity.instanceId(), identity.definitionId(), 1, "old"), item.registryRevision(), Map.of(), List.of()))).code());
        assertEquals(UNSUPPORTED_SCHEMA, assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(
                new WeaponIdentity(identity.instanceId(), identity.definitionId(), 2, identity.definitionRevision()), item.registryRevision(), Map.of(), List.of()))).code());
        assertEquals(UNKNOWN_ROLL, assertThrows(ItemValidationException.class, () -> registry.edit(item, Map.of(), List.of("damage_999999"))).code());
    }

    @Test void snapshotsAndRegistryCopyTheirInputsAndOnlyAllowNamedRolls() {
        var modifier = new StatModifier("roll:precision", StatKey.CRIT_CHANCE, ModifierOperation.FLAT, 5, 0);
        var modifiers = new ArrayList<>(List.of(modifier));
        var base = registry.definitions().get("ordinary");
        var custom = new ItemRegistry("roll-fixture-v1", List.of(new ItemDefinition(base.weapon(), "Test", "BOW", Set.of("precision"))),
                List.of(), Map.of("precision", modifiers));
        modifiers.clear();
        var ids = new ArrayList<>(List.of("precision"));
        var levels = new HashMap<String, Integer>();
        var rolled = custom.edit(custom.create("ordinary"), levels, ids);
        ids.clear();
        levels.put("duplex", 5);
        assertTrue(custom.resolve(rolled).statModifiers().contains(modifier));
        assertEquals(List.of("precision"), rolled.rolledModifierIds());
        assertTrue(rolled.enchantLevels().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> rolled.rolledModifierIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> custom.resolve(rolled).statModifiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> custom.definitions().clear());
        assertEquals(DUPLICATE_ROLL, assertThrows(ItemValidationException.class,
                () -> custom.edit(rolled, Map.of(), List.of("precision", "precision"))).code());
    }

    @Test void invalidTrustedCatalogsFailBeforeAdoption() {
        var original = registry.definitions().get("duplex");
        var lying = new WeaponDefinition("duplex", 1, CalibrationLoadouts.REVISION, WeaponDefinition.FiringMode.DRAWN_BOW,
                100, List.of(), List.of(new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ORDINARY)));
        assertThrows(IllegalArgumentException.class, () -> new ItemRegistry(CalibrationLoadouts.REVISION,
                List.of(new ItemDefinition(lying, "Lie", "BOW", Set.of())), List.of(registry.enchant("duplex")), Map.of()));
        assertThrows(IllegalStateException.class, () -> new ItemRegistry("fixture", List.of(original, original), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new StatModifier("bad", StatKey.FEROCITY, ModifierOperation.FLAT, Double.NaN, 0));
        var drawnOnly = new EnchantDefinition("drawn", "Drawn only", WeaponDefinition.EnchantmentKind.ORDINARY,
                Set.of(WeaponDefinition.FiringMode.DRAWN_BOW), Map.of(1, List.of()));
        assertEquals(INCOMPATIBLE_ENCHANT, assertThrows(ItemValidationException.class,
                () -> drawnOnly.validate(1, WeaponDefinition.FiringMode.SHORTBOW)).code());
    }

    @Test void resolvedWeaponProjectsEditedEnchantsAndRollsWithBaseModifiersExactlyOnce() {
        var base = registry.definitions().get("ordinary");
        var roll = new StatModifier("roll:precision", StatKey.CRIT_CHANCE, ModifierOperation.FLAT, 5, 0);
        var custom = new ItemRegistry("projection-v1", List.of(new ItemDefinition(base.weapon(), "Projection", "BOW", Set.of("precision"))),
                List.of(registry.enchant("vicious"), registry.enchant("duplex")), Map.of("precision", List.of(roll)));
        var before = custom.edit(custom.create("ordinary"), Map.of("duplex", 5), List.of());
        var edited = custom.edit(before, Map.of("duplex", 2, "vicious", 3), List.of("precision"));
        var weapon = custom.resolve(edited).resolvedWeapon();
        assertEquals(base.weapon().id(), weapon.id());
        assertEquals(base.weapon().schemaVersion(), weapon.schemaVersion());
        assertEquals(base.weapon().revision(), weapon.revision());
        assertEquals(base.weapon().firingMode(), weapon.firingMode());
        assertEquals(100, weapon.baseDamage());
        assertEquals(List.of(new WeaponDefinition.Enchantment("duplex", 2, WeaponDefinition.EnchantmentKind.ULTIMATE),
                new WeaponDefinition.Enchantment("vicious", 3, WeaponDefinition.EnchantmentKind.ORDINARY)), weapon.enchantments());
        var expected = new ArrayList<>(base.weapon().statModifiers());
        expected.add(roll);
        expected.add(new StatModifier("enchant:vicious", StatKey.FEROCITY, ModifierOperation.FLAT, 3, 0));
        assertEquals(expected.stream().sorted(StatModifier.EXPLANATION_ORDER).toList(), weapon.statModifiers());
        assertEquals(4, weapon.statModifiers().size());
        assertTrue(weapon.statModifiers().stream().noneMatch(m -> m.key() == StatKey.WEAPON_DAMAGE || m.key() == StatKey.CRIT_DAMAGE));
        assertEquals(Map.of("duplex", 5), before.enchantLevels());
    }
}
