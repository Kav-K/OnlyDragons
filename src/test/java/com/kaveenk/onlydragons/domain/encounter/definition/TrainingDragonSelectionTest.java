package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.encounter.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the explicit training projection retains the same sole type/table, changes HP and
 * policy identity, and leaves the bundled calibration untouched. A mismatched full-health
 * encounter must reject the retained selection; no native dragon is spawned.
 */
class TrainingDragonSelectionTest {
    @Test void trainingIsAnImmutableSelectionOfTheSameTypeWithoutMutatingCalibration() {
        var catalog=DragonCatalogLoader.calibration(CalibrationLoadouts.registry()).bundled();
        var old=catalog.select("test_dragon");var training=TrainingDragonSelection.from(old);
        assertEquals(1000,old.maxHealth());assertEquals(CombatProfile.calibration(),old.combatProfile());
        assertEquals(100_000,training.maxHealth());assertEquals(0,training.defense());
        assertEquals(old.identity().id(),training.identity().id());assertNotEquals(old.catalogIdentity(),training.catalogIdentity());
        assertEquals(CombatProfile.tempoDragon(),training.combatProfile());assertEquals(old.table(),training.table());
        assertThrows(IllegalArgumentException.class,()->new CombatEncounter(new TargetState(UUID.randomUUID(),UUID.randomUUID(),1000,1000,0),"test_dragon",training.combatProfile(),Optional.of(training)));
        assertEquals(old,catalog.select("test_dragon"));
    }
}
