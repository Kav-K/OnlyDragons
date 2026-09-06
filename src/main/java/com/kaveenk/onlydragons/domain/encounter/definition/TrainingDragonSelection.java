package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import java.util.Set;

/** Explicit longer training encounter of the sole test type; legacy catalog remains unchanged. */
public final class TrainingDragonSelection {
    private TrainingDragonSelection() {}

    public static DragonCatalog.Selection from(DragonCatalog.Selection calibration) {
        return select(calibration, true);
    }

    public static DragonCatalog.Selection select(DragonCatalog.Selection calibration, boolean training) {
        if (!calibration.identity().id().equals("test_dragon"))
            throw new IllegalArgumentException("Training requires test_dragon");
        var profile = CombatProfile.tempoDragon();
        return new DragonCatalog.Selection(new DefinitionIdentity(training ? "test-dragon-training-catalog" : "test-dragon-catalog", 1, "v2"),
                new DefinitionIdentity("test_dragon", 1, training ? "training-v2" : "v2"), training ? "Test Dragon (Training)" : "Test Dragon",
                training ? 100_000 : 1000, 0, profile, new PhaseProfile(new MechanicRevision("test-dragon-training-phase", "v2"),
                Set.of(profile.mechanic())), calibration.table());
    }
}
