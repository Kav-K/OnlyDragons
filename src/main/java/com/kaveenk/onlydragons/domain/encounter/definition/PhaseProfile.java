package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;
import java.util.Set;

/** Inert compatibility contract; does not implement movement or authorize native phase admission. */
public record PhaseProfile(MechanicRevision mechanic, Set<MechanicRevision> compatibleCombatProfiles) {
    public PhaseProfile {
        Objects.requireNonNull(mechanic, "phase mechanic");
        compatibleCombatProfiles = Set.copyOf(compatibleCombatProfiles);
        if (compatibleCombatProfiles.isEmpty()) throw new IllegalArgumentException("Missing compatible combat profiles");
    }
}
