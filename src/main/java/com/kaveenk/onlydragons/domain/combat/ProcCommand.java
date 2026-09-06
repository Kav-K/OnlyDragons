package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;
import java.util.UUID;

/**
 * A scheduled ferocity child with a frozen pre-cap damage basis and inherited crit.
 * Consumers revalidate encounter/target at dueTick and apply the cap once.
 * This command never authorizes another child roll or Duplex emission.
 */
public record ProcCommand(UUID procId, UUID parentImpactId, PhysicalImpact.Key origin,
                          UUID ownerId, UUID shotId, long dueTick, double preCapDamage,
                          CritOutcome crit, MechanicRevision mechanic, int fatalTempoSourceLevel) {
    public ProcCommand {
        Objects.requireNonNull(procId, "procId");
        Objects.requireNonNull(parentImpactId, "parentImpactId");
        if (procId.equals(parentImpactId)) throw new IllegalArgumentException("A proc cannot parent itself");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(shotId, "shotId");
        DomainChecks.nonNegative(dueTick, "dueTick");
        DomainChecks.nonNegative(preCapDamage, "preCapDamage");
        Objects.requireNonNull(crit, "crit");
        Objects.requireNonNull(mechanic, "mechanic");
        if (fatalTempoSourceLevel < 0 || fatalTempoSourceLevel > 5) {
            throw new IllegalArgumentException("fatalTempoSourceLevel must be 0 (ineligible) or 1-5");
        }
    }
}
