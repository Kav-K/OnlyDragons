package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.projectile.FiringRules;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Literal seven-tier calibration and current-profile routing oracles through the production
 * registry/stat factory. Separates F25 strict probability from F100 guaranteed virtual hits,
 * checks all six v4 book-edited bows and rejects forged revision/ID controls. Cadence results
 * are pure tick formulas, not a claim about received native held-use input.
 */
class ShortbowLoadoutsTest {
    private final ItemRegistry registry = CalibrationLoadouts.compatibleRegistry();

    @Test void literalTierTotalsResolveThroughProductionFactory() {
        var expected = Map.of("drawn_training_v4", List.of(0d, 0d, 10d), "swift_shortbow_v4", List.of(100d, 0d, 5d),
                "volley_shortbow_v4", List.of(400d, 0d, 2d), "volley_ferocity25_v4", List.of(400d, 25d, 2d),
                "volley_ferocity100_v4", List.of(400d, 100d, 2d), "volley_duplex_v4", List.of(400d, 25d, 2d),
                "volley_tempo_v4", List.of(400d, 25d, 2d));
        assertEquals(expected.keySet(), new java.util.HashSet<>(ShortbowLoadouts.ids()));
        for (var row : expected.entrySet()) {
            var item = registry.resolve(registry.create(row.getKey()));
            var stats = new StatSnapshotFactory(StatProfile.calibration()).create("test", item.resolvedWeapon(), ModifierSources.empty()).snapshot();
            assertEquals(List.of(100d, 0d, 50d), List.of(stats.raw(StatKey.WEAPON_DAMAGE), stats.raw(StatKey.CRIT_CHANCE), stats.raw(StatKey.CRIT_DAMAGE)));
            assertEquals(row.getValue(), List.of(stats.effective(StatKey.ATTACK_SPEED), stats.effective(StatKey.FEROCITY), (double) FiringRules.cooldown(stats.effective(StatKey.ATTACK_SPEED))));
            assertEquals(row.getKey().equals("drawn_training_v4") ? WeaponDefinition.FiringMode.DRAWN_BOW : WeaponDefinition.FiringMode.SHORTBOW, item.resolvedWeapon().firingMode());
            assertEquals(TracerProfile.AIMED_V3, TracerProfile.forDefinition(item.definition().weapon()));
            assertEquals("calibration-items-v4", item.instance().registryRevision());
        }
    }

    @Test void namedFerocityControlsKeepStrictChanceAndGuarantee() {
        var factory = new StatSnapshotFactory(StatProfile.calibration());
        for (var row : Map.of("volley_shortbow_v4", List.of(0, 0),
                "volley_ferocity25_v4", List.of(1, 0), "volley_ferocity100_v4", List.of(1, 1)).entrySet()) {
            var item = registry.resolve(registry.create(row.getKey()));
            double ferocity = factory.create("control", item.resolvedWeapon(), ModifierSources.empty())
                    .snapshot().effective(StatKey.FEROCITY);
            assertEquals(row.getValue(), List.of(
                    com.kaveenk.onlydragons.domain.enchant.Ferocity.count(ferocity, () -> Math.nextDown(.25)),
                    com.kaveenk.onlydragons.domain.enchant.Ferocity.count(ferocity, () -> .25)));
        }
    }

    @Test void separateUltimatesAndBookEditsRetainTrustedNewProfile() {
        for (var row : Map.of("volley_duplex_v4", "duplex", "volley_tempo_v4", "fatal_tempo").entrySet()) {
            var item = registry.create(row.getKey());
            assertEquals(Map.of("dragon_tracer", 5, row.getValue(), 5, "infinite_quiver", 10, "flame", 2), item.enchantLevels());
            assertThrows(ItemValidationException.class, () -> registry.edit(item, Map.of("duplex", 5, "fatal_tempo", 5), List.of()));
        }
        var original = registry.create("swift_shortbow_v4");
        var edited = registry.edit(original, Map.of("dragon_tracer", 5, "vicious", 5), List.of());
        assertEquals(original.identity(), edited.identity());
        var resolved = registry.resolve(edited);
        assertEquals(TracerProfile.AIMED_V3, TracerProfile.forDefinition(resolved.definition().weapon()));
        assertEquals(5, new StatSnapshotFactory(StatProfile.calibration()).create("edited", resolved.resolvedWeapon(), ModifierSources.empty()).snapshot().raw(StatKey.FEROCITY));
        var definition = resolved.definition().weapon();
        assertEquals(TracerProfile.CALIBRATION_V1, TracerProfile.forDefinition(new WeaponDefinition(definition.id(), 1, "fake", definition.firingMode(), 100, List.of(), List.of())));
        assertEquals(TracerProfile.CALIBRATION_V1, TracerProfile.forDefinition(new WeaponDefinition("fake", 1, "held-shortbows-v1", definition.firingMode(), 100, List.of(), List.of())));
        assertThrows(ItemValidationException.class, () -> registry.resolve(new ItemInstance(new WeaponIdentity(original.identity().instanceId(), original.identity().definitionId(), 1, "fake"), original.registryRevision(), Map.of(), List.of())));
        for (var item : registry.definitions().values()) {
            if (!ShortbowLoadouts.ids().contains(item.weapon().id())) assertEquals(
                    item.weapon().revision().equals(CalibrationLoadouts.FIRE_REVISION) ? TracerProfile.AIMED_V3
                            : item.weapon().id().equals("tracer_return_v2") ? TracerProfile.RETURN_V2 : TracerProfile.CALIBRATION_V1,
                    TracerProfile.forDefinition(item.weapon()));
        }
        assertEquals(34, registry.definitions().size());
    }
    @Test void allSixCurrentV4DefinitionsKeepAimedProfileAfterTracerBookEdits() {
        for (String id : List.of("ordinary_v4", "shortbow_v4", "quiver_v4", "flame_v4", "duplex_flame_v4", "tempo_flame_v4")) {
            var original = registry.create(id);
            var edited = registry.edit(original, Map.of("dragon_tracer", 5), List.of());
            assertEquals(original.identity(), edited.identity());
            var trusted = registry.resolve(edited).resolvedWeapon();
            assertEquals(TracerProfile.AIMED_V3, TracerProfile.forDefinition(trusted));
            assertEquals(5, trusted.enchantments().getFirst().level());
            assertEquals(TracerProfile.CALIBRATION_V1, TracerProfile.forDefinition(
                    new WeaponDefinition(id, 1, "fake", trusted.firingMode(), 100, List.of(), List.of())));
        }
        assertEquals(TracerProfile.CALIBRATION_V1, TracerProfile.forDefinition(
                new WeaponDefinition("fake", 1, CalibrationLoadouts.FIRE_REVISION,
                        WeaponDefinition.FiringMode.DRAWN_BOW, 100, List.of(), List.of())));
    }
}
