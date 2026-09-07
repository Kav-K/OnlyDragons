package com.kaveenk.onlydragons.domain.stats;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks complete immutable snapshots, enum-order values, raw/effective chance separation and
 * modifier/range validation. Hand-built maps deliberately bypass the resolver to isolate DTO
 * invariants; later caller mutations must not rewrite a captured snapshot.
 */
class StatContractsTest {
    @Test
    void snapshotRetainsRawCritAndDoesNotFollowCallerMutation() {
        var values = values(175);
        var modifiers = new ArrayList<>(List.of(
                new StatModifier("gear:bow", StatKey.WEAPON_DAMAGE, ModifierOperation.MULTIPLIER, 1.5, 2),
                new StatModifier("gear:bow", StatKey.WEAPON_DAMAGE, ModifierOperation.MULTIPLIER, 1.2, 1)));
        var snapshot = new StatSnapshot("equipment:7", values, modifiers);
        values.put(StatKey.CRIT_CHANCE, new StatValue(0, 0));
        modifiers.clear();
        assertEquals(175, snapshot.raw(StatKey.CRIT_CHANCE));
        assertEquals(1, snapshot.ordinaryCritProbability());
        assertEquals(List.of(1.2, 1.5), snapshot.provenance().stream().map(StatModifier::amount).toList());
        assertEquals(List.of(StatKey.values()), new ArrayList<>(snapshot.values().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.provenance().clear());
    }

    @Test
    void ordinaryCritUsesEffectiveChanceWithoutDiscardingRawValue() {
        var values = values(175);
        values.put(StatKey.CRIT_CHANCE, new StatValue(175, 25));
        var snapshot = new StatSnapshot("cap-profile:1", values, List.of());
        assertEquals(175, snapshot.raw(StatKey.CRIT_CHANCE));
        assertEquals(0.25, snapshot.ordinaryCritProbability());
        assertEquals(0, new StatSnapshot("zero", values(0), List.of()).ordinaryCritProbability());
    }

    @Test
    void incompleteOrInvalidSnapshotsCannotEnterCombat() {
        assertThrows(NullPointerException.class, () -> new StatSnapshot("v1", Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StatSnapshot(" ", values(0), List.of()));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new StatValue(invalid, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new StatValue(5, 6));
    }

    @Test
    void definitionsSeparateValidationRangesFromProfileCaps() {
        var chance = new StatDefinition(StatKey.CRIT_CHANCE, 0, 0, 1000);
        assertEquals(175, chance.validateRaw(175));
        assertEquals(StatUnit.PERCENTAGE_POINTS, chance.unit());
        assertThrows(IllegalArgumentException.class, () -> chance.validateRaw(1001));
        assertThrows(IllegalArgumentException.class, () -> chance.validateRaw(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new StatDefinition(StatKey.DEFENSE, 5, 10, 20));
    }

    @Test
    void modifierContractAllowsDebuffsButRejectsInvalidFactors() {
        assertEquals(-25, new StatModifier("effect:weakness", StatKey.WEAPON_DAMAGE,
                ModifierOperation.ADDITIVE_PERCENT, -25, 0).amount());
        assertThrows(IllegalArgumentException.class, () -> new StatModifier("gear", StatKey.WEAPON_DAMAGE,
                ModifierOperation.MULTIPLIER, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StatModifier("gear", StatKey.WEAPON_DAMAGE,
                ModifierOperation.FLAT, Double.NaN, 0));
    }

    /**
     * Creates a complete zero-filled map with identical raw/effective chance so tests can mutate
     * one boundary independently without depending on calibration defaults.
     */
    private static EnumMap<StatKey, StatValue> values(double chance) {
        var values = new EnumMap<StatKey, StatValue>(StatKey.class);
        for (StatKey key : StatKey.values()) values.put(key, new StatValue(0, 0));
        values.put(StatKey.CRIT_CHANCE, new StatValue(chance, chance));
        return values;
    }
}
