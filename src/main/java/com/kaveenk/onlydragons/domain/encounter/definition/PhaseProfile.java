package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;
import java.util.Set;

/**
 * Inert compatibility contract; does not implement movement or authorize native phase admission.
 * <p>
 * @param mechanic nonnull phase-contract identity
 * @param compatibleCombatProfiles nonempty immutable copy of permitted exact combat revision labels
 */
public record PhaseProfile(MechanicRevision mechanic, Set<MechanicRevision> compatibleCombatProfiles) {
    /**
     * Copies nonnull compatibility entries; loader checks that all references exist in its trusted
     * combat profile set. No native dragon phase transitions are performed.
     */
    public PhaseProfile {
        Objects.requireNonNull(mechanic, "phase mechanic");
        compatibleCombatProfiles = Set.copyOf(compatibleCombatProfiles);
        if (compatibleCombatProfiles.isEmpty()) throw new IllegalArgumentException("Missing compatible combat profiles");
    }
}
