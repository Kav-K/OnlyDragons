package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class EnchantRulesTest {
    @ParameterizedTest
    @CsvSource({"0,0,0", "25,0.249999,1", "25,0.25,0", "99,0.98,1", "99,0.99,0",
            "100,0.99,1", "101,0.009,2", "101,0.01,1", "250,0.499999,3", "250,0.5,2",
            "499,0.98,5", "499,0.99,4", "500,0.99,5", "750,0,5"})
    void controlledCounts(double ferocity, double sample, int expected) {
        assertEquals(expected, Ferocity.count(ferocity, () -> sample));
    }

    @Test void capAndRandomValidation() {
        assertEquals(0, Ferocity.effective(0, 200));
        assertEquals(75, Ferocity.effective(25, 200));
        assertEquals(500, Ferocity.effective(Double.MAX_VALUE, 200));
        assertEquals(5, Ferocity.count(500, () -> { throw new AssertionError("No fractional draw at a whole hundred"); }));
        for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Ferocity.count(value, () -> 0));
            assertThrows(IllegalArgumentException.class, () -> Ferocity.effective(value, 0));
            assertThrows(IllegalArgumentException.class, () -> Ferocity.count(25, () -> value));
        }
        assertThrows(IllegalArgumentException.class, () -> Ferocity.count(25, () -> 1));
        assertThrows(IllegalArgumentException.class, () -> Ferocity.effective(25, 201));
    }

    @ParameterizedTest @CsvSource({"1,10,20", "2,20,10", "3,30,7", "4,40,5", "5,50,4"})
    void eachTempoLevelCapsAndExpires(int level, int firstBonus, int hits) {
        var state = new TempoState(0, 0).hit(level, 10);
        assertEquals(firstBonus, state.bonusAt(10));
        for (int hit = 1; hit < hits; hit++) state = state.hit(level, 10);
        assertEquals(200, state.bonusAt(69));
        assertEquals(0, state.bonusAt(70));
        assertEquals(0, state.bonusAt(71));
        assertEquals(new TempoState(firstBonus, 130), state.hit(level, 70));
    }

    @Test void mixedLevelsShareOnePoolAndExpiredStateDoesNotReturn() {
        var first = new TempoState(0, 0).hit(5, 1);
        var mixed = first.hit(1, 2);
        assertEquals(60, mixed.bonusAt(61));
        assertEquals(0, mixed.bonusAt(62));
        assertEquals(50, first.bonusAt(1));
        assertThrows(IllegalArgumentException.class, () -> mixed.hit(0, 3));
        assertThrows(ArithmeticException.class, () -> mixed.hit(1, Long.MAX_VALUE));
    }

    @ParameterizedTest @CsvSource({"1,.08", "2,.16", "3,.24", "4,.32", "5,.4", "6,.5", "7,.65"})
    void powerTable(int level, double expected) {
        assertEquals(expected, EnchantEffects.calibration().modifiers(shot("power", level), new Vector3(0, 0, 0), false)
                .additiveFractions().get("enchant:power"));
    }

    @ParameterizedTest @CsvSource({"0,0", "9.99,.00999", "10,.01", "10.01,.01001", "1000,1"})
    void snipeIsContinuousDisplacement(double distance, double expected) {
        assertEquals(expected, EnchantEffects.snipeFraction(1, new Vector3(0, 0, 0), new Vector3(distance, 0, 0)), 1e-12);
    }

    @Test void curvedFlightAndOwnerMovementCannotEnterSnipeInput() {
        var shot = shot("snipe", 4);
        var effects = EnchantEffects.calibration();
        assertEquals(.04, effects.modifiers(shot, new Vector3(6, 8, 0), false).additiveFractions().get("enchant:snipe"), 1e-12);
        assertEquals(0, effects.modifiers(shot, shot.launchPosition(), false).additiveFractions().get("enchant:snipe"));
        // No path accumulator or current player location exists in the API.
        assertEquals(0, shot.launchPosition().x());
    }

    @Test void viciousIsAlreadyCapturedByTrustedRegistryExactlyOnce() {
        var registry = CalibrationLoadouts.registry();
        var resolved = registry.resolve(registry.edit(registry.create("ordinary"), Map.of("vicious", 5), List.of()));
        var snapshot = new StatSnapshotFactory(StatProfile.calibration()).create("v1", resolved.resolvedWeapon(), ModifierSources.empty()).snapshot();
        assertEquals(5, snapshot.effective(StatKey.FEROCITY));
        assertEquals(DamageModifiers.none(), EnchantEffects.calibration().modifiers(shot("vicious", 5), new Vector3(0, 0, 0), false));
    }

    @Test void gravityAndAliasRequireExplicitNamedTableAndOverloadRemainsDeferred() {
        var deferred = EnchantEffects.calibration();
        assertThrows(IllegalStateException.class, () -> deferred.modifiers(shot("gravity", 1), new Vector3(0, 0, 0), true));
        assertThrows(IllegalArgumentException.class, () -> deferred.modifiers(shot("dragon_hunter", 1), new Vector3(0, 0, 0), true));
        assertThrows(IllegalStateException.class, () -> deferred.modifiers(shot("overload", 1), new Vector3(0, 0, 0), true));
        var explicit = new EnchantEffects(new EnchantEffects.GravityProfile("experimental-test-only", Map.of(1, .123), true),
                new EnchantEffects.OverloadPolicy() {
                    public String revision() { return "extension-test-only"; }
                    public DamageModifiers modifiers(ShotContext shot, int level) {
                        assertEquals(175, shot.stats().raw(StatKey.CRIT_CHANCE));
                        return new DamageModifiers(Map.of(), Map.of("fixture", 1.25));
                    }
                });
        assertEquals(.123, explicit.modifiers(shot("gravity", 1), new Vector3(0, 0, 0), true)
                .additiveFractions().get("enchant:gravity/experimental-test-only"));
        assertEquals(DamageModifiers.none(), explicit.modifiers(shot("gravity", 1), new Vector3(0, 0, 0), false));
        assertEquals(.123, explicit.modifiers(shot("dragon_hunter", 1), new Vector3(0, 0, 0), true)
                .additiveFractions().get("enchant:gravity/experimental-test-only"));
        assertEquals(1.25, explicit.modifiers(shot("overload", 1), new Vector3(0, 0, 0), true)
                .multipliers().get("enchant:overload/extension-test-only/fixture"));
    }

    @Test void unsupportedLevelsAndUltimateConflictsStayRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnchantEffects.calibration().modifiers(shot("power", 8), new Vector3(0, 0, 0), false));
        var registry = CalibrationLoadouts.registry();
        assertThrows(ItemValidationException.class, () -> registry.edit(registry.create("ordinary"), Map.of("fatal_tempo", 5, "duplex", 5), List.of()));
        assertThrows(ItemValidationException.class, () -> registry.edit(registry.create("ordinary"), Map.of("snipe", 5), List.of()));
    }

    private static ShotContext shot(String enchant, int level) {
        var stats = new StatResolver(StatProfile.calibration()).resolve("v1", Map.of(StatKey.CRIT_CHANCE, 175.0), ModifierSources.empty()).snapshot();
        return new ShotContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, Optional.empty(), UUID.randomUUID(),
                new WeaponIdentity(UUID.randomUUID(), "test", 1, "v1"), stats,
                List.of(new WeaponDefinition.Enchantment(enchant, level, WeaponDefinition.EnchantmentKind.ORDINARY)),
                CombatProfile.calibration().mechanic(), 0, new Vector3(0, 0, 0), new Vector3(1, 0, 0), CritOutcome.CRITICAL, 1, 1);
    }
}
