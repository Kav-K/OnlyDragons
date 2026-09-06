package com.kaveenk.onlydragons.domain.stats;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.domain.stats.StatKey.*;
import static com.kaveenk.onlydragons.domain.stats.ModifierOperation.*;

import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatResolverTest {
    private final StatProfile profile = StatProfile.calibration();
    private final StatResolver resolver = new StatResolver(profile);
    private StatModifier modifier(String source, StatKey key, ModifierOperation op, double amount, int order) {
        return new StatModifier(source, key, op, amount, order);
    }
    private ExplainedStatSnapshot resolve(StatKey key, double base, StatModifier... modifiers) {
        var sources = ModifierSources.empty();
        for (var m : modifiers) {
            var group = new ArrayList<>(sources.sources().getOrDefault(m.sourceId(), List.of()));
            group.add(m);
            sources = sources.replace(m.sourceId(), group);
        }
        return resolver.resolve("test-1", Map.of(key, base), sources);
    }

    @Test void completeDefaultsAndProfileIdentity() {
        var result = resolver.resolve("snapshot-9", Map.of(), ModifierSources.empty());
        assertEquals(7, result.snapshot().values().size());
        assertEquals(7, result.explanations().size());
        assertEquals("snapshot-9", result.snapshot().revision());
        assertEquals("stats-calibration-v1", result.profileRevision());
        assertEquals(20, result.snapshot().raw(MAX_HEALTH));
        assertEquals(50, result.snapshot().raw(CRIT_DAMAGE));
        for (var key : List.of(WEAPON_DAMAGE, CRIT_CHANCE, FEROCITY, ATTACK_SPEED, DEFENSE)) {
            assertEquals(0, result.snapshot().raw(key));
        }
    }

    @Test void layersAndFractionalExplanationMatchGoldenFixture() {
        var result = resolve(WEAPON_DAMAGE, 100.25,
                modifier("gear", WEAPON_DAMAGE, FLAT, 0.75, 0),
                modifier("gear", WEAPON_DAMAGE, ADDITIVE_PERCENT, 25, 0),
                modifier("gear", WEAPON_DAMAGE, ADDITIVE_PERCENT, 25, 1),
                modifier("gear", WEAPON_DAMAGE, MULTIPLIER, 2, 20),
                modifier("gear", WEAPON_DAMAGE, MULTIPLIER, 0.5, 10));
        assertEquals(151.5, result.snapshot().raw(WEAPON_DAMAGE), 1e-12);
        var explanation = result.explanations().get(WEAPON_DAMAGE);
        assertEquals(100.25, explanation.base());
        assertEquals(List.of(101.0, 151.5, 75.75, 151.5, 151.5),
                explanation.steps().stream().map(ExplainedStatSnapshot.Step::result).toList());
    }

    @Test void deterministicSourcesInputPermutationAndMultiplierTies() {
        var a = modifier("a", WEAPON_DAMAGE, MULTIPLIER, 2, 0);
        var b = modifier("b", WEAPON_DAMAGE, MULTIPLIER, 0.5, 0);
        var a2 = modifier("a", WEAPON_DAMAGE, MULTIPLIER, 0.25, 0);
        var first = resolve(WEAPON_DAMAGE, 100, b, a, a2);
        var second = resolve(WEAPON_DAMAGE, 100, a2, a, b);
        assertEquals(first, second);
        assertEquals(List.of(a2, a, b), first.snapshot().provenance());
        assertEquals(List.of(25.0, 50.0, 25.0), first.explanations().get(WEAPON_DAMAGE).steps().stream()
                .filter(step -> step.stage().equals("multiplier")).map(ExplainedStatSnapshot.Step::result).toList());
    }

    @Test void refreshReplacesWholeCollectionAndOldSnapshotsStayImmutable() {
        var input = new ArrayList<>(List.of(modifier("gear", FEROCITY, FLAT, 25, 0),
                modifier("gear", FEROCITY, MULTIPLIER, 2, 0), modifier("gear", FEROCITY, MULTIPLIER, 3, 1)));
        var oldSources = ModifierSources.empty().replace("gear", input);
        var old = resolver.resolve("old", Map.of(), oldSources);
        input.clear();
        var refreshed = oldSources.replace("gear", List.of(modifier("gear", FEROCITY, FLAT, 10, 0)));
        assertEquals(10, resolver.resolve("new", Map.of(), refreshed).snapshot().raw(FEROCITY));
        assertEquals(150, old.snapshot().raw(FEROCITY));
        assertEquals(150, resolver.resolve("old", Map.of(), oldSources).snapshot().raw(FEROCITY));
        assertEquals(0, resolver.resolve("removed", Map.of(), refreshed.replace("gear", List.of())).snapshot().raw(FEROCITY));
        assertThrows(UnsupportedOperationException.class, () -> oldSources.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> oldSources.sources().get("gear").clear());
        assertThrows(UnsupportedOperationException.class, () -> old.snapshot().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.snapshot().provenance().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.explanations().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.explanations().get(FEROCITY).steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.explanations().get(FEROCITY).steps().getFirst().contributions().clear());
        assertThrows(IllegalArgumentException.class, () -> refreshed.replace("wrong", oldSources.modifiers()));
    }

    @Test void capsAndOrdinaryProbabilityHaveSeparateBoundaries() {
        for (double value : new double[]{0, 25, 99, 100, 101, 250, 499, 499.999, 500, 500.001, 750.5}) {
            var result = resolve(FEROCITY, value).snapshot();
            assertEquals(value, result.raw(FEROCITY));
            assertEquals(Math.min(500, value), result.effective(FEROCITY));
        }
        for (double value : new double[]{0, 0.5, 99.99, 100, 100.01, 175}) {
            var result = resolve(CRIT_CHANCE, value).snapshot();
            assertEquals(value, result.raw(CRIT_CHANCE));
            assertEquals(Math.min(1, value / 100), result.ordinaryCritProbability());
        }
        for (var key : StatKey.values()) {
            var definition = profile.definitions().get(key);
            assertDoesNotThrow(() -> resolve(key, definition.minimumValue()));
            assertDoesNotThrow(() -> resolve(key, definition.maximumValue()));
            assertThrows(IllegalArgumentException.class, () -> resolve(key, Math.nextDown(definition.minimumValue())));
            assertThrows(IllegalArgumentException.class, () -> resolve(key, Math.nextUp(definition.maximumValue())));
        }
    }

    @Test void nonfiniteOverflowAndNegativeLayersAreRejectedBeforeCaps() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, value));
            assertThrows(IllegalArgumentException.class, () -> modifier("x", FEROCITY, FLAT, value, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, 1,
                modifier("x", FEROCITY, FLAT, Double.MAX_VALUE, 0), modifier("x", FEROCITY, FLAT, Double.MAX_VALUE, 1)));
        assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, 2,
                modifier("x", FEROCITY, MULTIPLIER, Double.MAX_VALUE, 0), modifier("x", FEROCITY, MULTIPLIER, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, 1, modifier("x", FEROCITY, FLAT, -2, 0)));
        assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, 1, modifier("x", FEROCITY, ADDITIVE_PERCENT, -100.01, 0)));
        assertEquals(0, resolve(FEROCITY, 1, modifier("x", FEROCITY, ADDITIVE_PERCENT, -100, 0)).snapshot().raw(FEROCITY));
        assertEquals(0.5, resolve(FEROCITY, 1, modifier("x", FEROCITY, FLAT, -0.5, 0)).snapshot().raw(FEROCITY));
        assertThrows(IllegalArgumentException.class, () -> resolve(FEROCITY, 1000000, modifier("x", FEROCITY, FLAT, 1, 0)));
    }

    @Test void weaponBaseAppliedOnceThenModifiersAndOtherSources() {
        var weapon = new WeaponDefinition("bow", 1, "bow-v1", WeaponDefinition.FiringMode.DRAWN_BOW, 100,
                List.of(modifier("weapon", WEAPON_DAMAGE, FLAT, 10, 0)), List.of());
        var additional = ModifierSources.empty().replace("gear", List.of(modifier("gear", WEAPON_DAMAGE, ADDITIVE_PERCENT, 50, 0)));
        var factory = new StatSnapshotFactory(profile);
        assertEquals(165, factory.create("one", weapon, additional).snapshot().raw(WEAPON_DAMAGE));
        assertThrows(IllegalArgumentException.class, () -> factory.create("bad", weapon,
                additional.replace("weapon", weapon.statModifiers())));
    }

    @Test void sharedCalibrationKeepsCritDamageBaselineAndProjectedModifiersOnce() {
        var factory = new StatSnapshotFactory(profile);
        // T02 projects validated definition/enchant/roll modifiers into one WeaponDefinition.
        // This fixture asserts the stable T00 boundary without importing an unmerged item API.
        var ordinary = new WeaponDefinition("ordinary", 1, "fixture-v1", WeaponDefinition.FiringMode.DRAWN_BOW,
                100, List.of(), List.of());
        var baseline = factory.create("ordinary", ordinary, ModifierSources.empty()).snapshot();
        assertEquals(100, baseline.raw(WEAPON_DAMAGE));
        assertEquals(50, baseline.raw(CRIT_DAMAGE));
        assertEquals(0, baseline.raw(CRIT_CHANCE));
        assertEquals(0, baseline.raw(FEROCITY));
        var projected = new WeaponDefinition("projected", 1, "fixture-v1", WeaponDefinition.FiringMode.DRAWN_BOW,
                100, List.of(modifier("item:definition", CRIT_CHANCE, FLAT, 100, 0),
                modifier("item:enchant", FEROCITY, FLAT, 25, 0),
                modifier("item:roll", WEAPON_DAMAGE, FLAT, 2.5, 0)), List.of());
        var equipped = factory.create("projected", projected, ModifierSources.empty()).snapshot();
        assertEquals(102.5, equipped.raw(WEAPON_DAMAGE));
        assertEquals(50, equipped.raw(CRIT_DAMAGE));
        assertEquals(100, equipped.raw(CRIT_CHANCE));
        assertEquals(1, equipped.ordinaryCritProbability());
        assertEquals(25, equipped.raw(FEROCITY));
        assertEquals(3, equipped.provenance().size());
    }

    @Test void strictProfileLoadingAndImmutableCandidateValidation() throws Exception {
        String text;
        try (var input = StatProfile.class.getResourceAsStream("/stats/calibration-v1.properties")) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String invalid : List.of(text + "\nunknown=1", text + "\nversion=2",
                text.replace("ferocity.cap=500", "ferocity.cap=NaN"),
                text.replace("version=1", "version=0"), text.replace("negativeResults=reject", "negativeResults=clamp"),
                text.replace("defense.default=0", "defense.default=-1"),
                text.replace("attack_speed.cap=1000000", "attack_speed.cap=1000001"),
                text.replace("crit_chance.default=0", ""))) {
            assertThrows(IllegalArgumentException.class, () -> StatProfile.load(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
        var definitions = new EnumMap<>(profile.definitions());
        var caps = new EnumMap<>(profile.effectiveCaps());
        caps.put(FEROCITY, 250.0);
        var changed = new StatProfile("custom", 2, definitions, caps);
        caps.clear(); definitions.clear();
        assertEquals(250, new StatResolver(changed).resolve("new", Map.of(FEROCITY, 300.0), ModifierSources.empty()).snapshot().effective(FEROCITY));
        assertEquals(500, profile.effectiveCaps().get(FEROCITY));
        assertThrows(UnsupportedOperationException.class, () -> changed.effectiveCaps().clear());
    }
}
