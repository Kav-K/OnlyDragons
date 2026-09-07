package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable ledger output. The future combat authority, not this DTO, applies health/death once. */
public record DamageResult(UUID impactId, Optional<UUID> parentImpactId, PhysicalImpact.Key origin,
                           UUID ownerId, UUID shotId, Kind kind, long tick, MechanicRevision mechanic,
                           Amounts amounts, CritOutcome crit, double effectiveFerocity,
                           Map<String, Double> modifierBreakdown, Optional<RejectionReason> rejectionReason) {
    public enum Kind { PHYSICAL, DUPLEX, FEROCITY, FIRE }
    public enum RejectionReason {
        CANCELLED, UNOWNED, WRONG_ENCOUNTER, TARGET_DEAD, INVALID_TARGET, DUPLICATE_IMPACT,
        OUTSIDE_ARENA, PROJECTILE_REMOVED, ENCOUNTER_ENDED
    }

    /** Health requested, actual HP loss, and score are deliberately separate quantities. */
    public record Amounts(double rawOffense, double mitigatedDamage, double cappedDamage,
                          double requestedHealthDamage, double actualHealthDamage, double contributionDamage) {
        public Amounts {
            DomainChecks.nonNegative(rawOffense, "rawOffense");
            DomainChecks.nonNegative(mitigatedDamage, "mitigatedDamage");
            DomainChecks.nonNegative(cappedDamage, "cappedDamage");
            DomainChecks.nonNegative(requestedHealthDamage, "requestedHealthDamage");
            DomainChecks.nonNegative(actualHealthDamage, "actualHealthDamage");
            DomainChecks.nonNegative(contributionDamage, "contributionDamage");
            if (actualHealthDamage > requestedHealthDamage) {
                throw new IllegalArgumentException("Actual HP loss exceeds requested HP loss");
            }
        }
    }

    public DamageResult {
        Objects.requireNonNull(impactId, "impactId");
        Objects.requireNonNull(parentImpactId, "parentImpactId");
        if (parentImpactId.filter(impactId::equals).isPresent()) throw new IllegalArgumentException("An impact cannot parent itself");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(shotId, "shotId");
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.FEROCITY || kind == Kind.FIRE) && parentImpactId.isEmpty()) throw new IllegalArgumentException("Ferocity requires a parent impact");
        DomainChecks.nonNegative(tick, "tick");
        Objects.requireNonNull(mechanic, "mechanic");
        Objects.requireNonNull(amounts, "amounts");
        Objects.requireNonNull(crit, "crit");
        DomainChecks.nonNegative(effectiveFerocity, "effectiveFerocity");
        var modifiers = new TreeMap<String, Double>();
        Objects.requireNonNull(modifierBreakdown, "modifierBreakdown").forEach((id, value) ->
                modifiers.put(DomainChecks.text(id, "modifier id"), DomainChecks.finite(Objects.requireNonNull(value), "modifier value")));
        modifierBreakdown = Collections.unmodifiableMap(modifiers);
        Objects.requireNonNull(rejectionReason, "rejectionReason");
        if (rejectionReason.isPresent() && (amounts.actualHealthDamage() != 0 || amounts.contributionDamage() != 0)) {
            throw new IllegalArgumentException("Rejected damage cannot reduce HP or earn score");
        }
    }

    public boolean accepted() { return rejectionReason.isEmpty(); }
}
