package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A scheduled ferocity child with a frozen pre-cap damage basis and inherited crit.
 * Consumers revalidate encounter/target at dueTick and apply the cap once.
 * This command never authorizes another child roll or Duplex emission.
 * <p>
 * {@link com.kaveenk.onlydragons.application.proc.ProcCoordinator} owns count, timing and admission;
 * {@link com.kaveenk.onlydragons.domain.encounter.CombatEncounter} verifies parent provenance.
 * @param procId nonnull stable child ID distinct from parentImpactId
 * @param parentImpactId nonnull accepted physical/Duplex impact ID
 * @param origin nonnull original physical key
 * @param ownerId nonnull captured shooter UUID
 * @param shotId nonnull captured trigger identity
 * @param dueTick nonnegative game tick; encounter additionally checks parent time
 * @param preCapDamage finite nonnegative already-mitigated parent basis, not capped output
 * @param crit nonnull inherited ordinary crit
 * @param mechanic nonnull frozen policy identity
 * @param fatalTempoSourceLevel captured refresh eligibility: 0 absent, 1–5 eligible
 * @param healthSnapshot nonnull optional pre-parent HP policy; required by active-level profiles
 */
public record ProcCommand(UUID procId, UUID parentImpactId, PhysicalImpact.Key origin,
                          UUID ownerId, UUID shotId, long dueTick, double preCapDamage,
                          CritOutcome crit, MechanicRevision mechanic, int fatalTempoSourceLevel,
                          Optional<ProcHealthSnapshot> healthSnapshot) {
    /** Legacy fixed-profile command. Level-based profiles require explicit admission provenance.
     * @param procId nonnull stable child ID distinct from parentImpactId
     * @param parentImpactId nonnull accepted physical/Duplex impact ID
     * @param origin nonnull original physical key
     * @param ownerId nonnull captured shooter UUID
     * @param shotId nonnull captured trigger identity
     * @param dueTick nonnegative game tick; encounter additionally checks parent time
     * @param preCapDamage finite nonnegative already-mitigated parent basis, not capped output
     * @param crit nonnull inherited ordinary crit
     * @param mechanic nonnull frozen policy identity
     * @param fatalTempoSourceLevel captured refresh eligibility: 0 absent, 1\u20135 eligible
     */
    public ProcCommand(UUID procId, UUID parentImpactId, PhysicalImpact.Key origin, UUID ownerId,
                       UUID shotId, long dueTick, double preCapDamage, CritOutcome crit,
                       MechanicRevision mechanic, int fatalTempoSourceLevel) {
        this(procId, parentImpactId, origin, ownerId, shotId, dueTick, preCapDamage, crit, mechanic,
                fatalTempoSourceLevel, Optional.empty());
    }
    /**
     * Rejects malformed IDs, time, numeric basis and refresh levels. Full parent matching is deferred
     * to the encounter; a valid DTO is not independent authorization to enqueue another child.
     * @param procId nonnull stable child ID distinct from parentImpactId
     * @param parentImpactId nonnull accepted physical/Duplex impact ID
     * @param origin nonnull original physical key
     * @param ownerId nonnull captured shooter UUID
     * @param shotId nonnull captured trigger identity
     * @param dueTick nonnegative game tick; encounter additionally checks parent time
     * @param preCapDamage finite nonnegative already-mitigated parent basis, not capped output
     * @param crit nonnull inherited ordinary crit
     * @param mechanic nonnull frozen policy identity
     * @param fatalTempoSourceLevel captured refresh eligibility: 0 absent, 1\u20135 eligible
     * @param healthSnapshot nonnull optional pre-parent HP policy; required by active-level profiles
     */
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
        Objects.requireNonNull(healthSnapshot, "healthSnapshot");
        if (fatalTempoSourceLevel < 0 || fatalTempoSourceLevel > 5) {
            throw new IllegalArgumentException("fatalTempoSourceLevel must be 0 (ineligible) or 1-5");
        }
    }
}
