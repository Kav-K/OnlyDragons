package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import java.util.Set;

/** Explicit longer training encounter of the sole test type; legacy catalog remains unchanged. */
public final class TrainingDragonSelection {
    private TrainingDragonSelection() {}

    /**
     * Returns the explicit 100,000-HP training selection by delegating to select(calibration,true);
     * retains the original inert table without changing the calibration value.
     */
    public static DragonCatalog.Selection from(DragonCatalog.Selection calibration) {
        return select(calibration, true);
    }

    /**
     * Requires the sole test_dragon type, then constructs explicit v2 identities, zero defense and
     * {@link CombatProfile#tempoDragon()}. Training selects 100,000 HP; standard selects 1,000 HP.
     * The original table is retained exactly; this pure projection neither mutates catalog state
     * nor spawns an entity. Other type IDs reject with IllegalArgumentException.
     */
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
