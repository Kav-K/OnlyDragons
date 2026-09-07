package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Immutable ledger output. {@link com.kaveenk.onlydragons.domain.encounter.CombatEncounter}
 * applies health/death once; constructing this DTO grants no damage.
 * <p>
 * Returned rejections are traceable but are not retained in the encounter's accepted ledger.
 * @param impactId nonnull stable identity of this physical hit or virtual child
 * @param parentImpactId nonnull optional accepted parent impact; required for FEROCITY/FIRE
 * @param origin nonnull original physical key, retained by virtual children
 * @param ownerId nonnull captured shooter UUID, independent of current login
 * @param shotId nonnull accepted trigger/group identity
 * @param kind nonnull physical or virtual source classification
 * @param tick nonnegative receiver commit/evaluation game tick
 * @param mechanic nonnull retained encounter policy identity
 * @param amounts nonnull independent HP and score amounts
 * @param crit nonnull captured ordinary crit, inherited by children
 * @param effectiveFerocity finite nonnegative diagnostic value
 * @param modifierBreakdown copied sorted map of nonblank names to finite diagnostic values
 * @param rejectionReason nonnull optional rejection; empty means accepted, including zero damage
 */
public record DamageResult(UUID impactId, Optional<UUID> parentImpactId, PhysicalImpact.Key origin,
                           UUID ownerId, UUID shotId, Kind kind, long tick, MechanicRevision mechanic,
                           Amounts amounts, CritOutcome crit, double effectiveFerocity,
                           Map<String, Double> modifierBreakdown, Optional<RejectionReason> rejectionReason) {
    /**
     * PHYSICAL and DUPLEX are real-arrow impacts; FEROCITY and FIRE are bounded virtual damage
     * commands. Only accepted physical kinds may parent the implemented virtual effects.
     */
    public enum Kind {
        /** Primary real-arrow impact. */ PHYSICAL,
        /** Secondary real-arrow impact with captured Duplex shot scaling. */ DUPLEX,
        /** Virtual child inheriting its accepted physical parent’s damage basis. */ FEROCITY,
        /** Virtual owned burn strike retaining physical source provenance. */ FIRE
    }
    /**
     * Zero-credit boundary outcomes. These describe admission/ownership/lifecycle rejection,
     * not malformed numeric inputs, which normally throw before commit.
     */
    public enum RejectionReason {
        /** An external cancellation vetoed the candidate impact. */ CANCELLED,
        /** The candidate lacks accepted plugin ownership. */ UNOWNED,
        /** Captured encounter identity differs from this authority. */ WRONG_ENCOUNTER,
        /** Domain health already reached zero before admission. */ TARGET_DEAD,
        /** The candidate does not identify an eligible encounter target. */ INVALID_TARGET,
        /** This physical key or child identity was already accepted. */ DUPLICATE_IMPACT,
        /** The adapter rejected the candidate’s arena bounds. */ OUTSIDE_ARENA,
        /** The owned projectile retired before an admissible impact. */ PROJECTILE_REMOVED,
        /** The captured encounter generation has ended. */ ENCOUNTER_ENDED
    }

    /**
     * Health requested, actual HP loss, and score are deliberately separate quantities.
     * <p>
     * For capped damage 1,000, HP fraction 0.25 and 20 remaining HP, requested/actual/credit
     * are 250/20/1,000. The constructor does not impose a cap-policy equation.
     * @param rawOffense finite nonnegative offense before mitigation
     * @param mitigatedDamage finite nonnegative offense after defense, before cap
     * @param cappedDamage finite nonnegative per-hit capped basis
     * @param requestedHealthDamage finite nonnegative HP request after the selected fraction
     * @param actualHealthDamage finite nonnegative HP loss no greater than the request
     * @param contributionDamage finite nonnegative ledger credit, not clipped to remaining HP
     */
    public record Amounts(double rawOffense, double mitigatedDamage, double cappedDamage,
                          double requestedHealthDamage, double actualHealthDamage, double contributionDamage) {
        /**
         * Rejects invalid amounts and actual HP exceeding requested HP; stores full precision.
         * @param rawOffense finite nonnegative offense before mitigation
         * @param mitigatedDamage finite nonnegative offense after defense, before cap
         * @param cappedDamage finite nonnegative per-hit capped basis
         * @param requestedHealthDamage finite nonnegative HP request after the selected fraction
         * @param actualHealthDamage finite nonnegative HP loss no greater than the request
         * @param contributionDamage finite nonnegative ledger credit, not clipped to remaining HP
         */
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

    /**
     * Validates identities/ancestry and freezes diagnostics. Rejections must have zero actual HP
     * and credit; construction alone cannot establish that a parent or collision really exists.
     * @param impactId nonnull stable identity of this physical hit or virtual child
     * @param parentImpactId nonnull optional accepted parent impact; required for FEROCITY/FIRE
     * @param origin nonnull original physical key, retained by virtual children
     * @param ownerId nonnull captured shooter UUID, independent of current login
     * @param shotId nonnull accepted trigger/group identity
     * @param kind nonnull physical or virtual source classification
     * @param tick nonnegative receiver commit/evaluation game tick
     * @param mechanic nonnull retained encounter policy identity
     * @param amounts nonnull independent HP and score amounts
     * @param crit nonnull captured ordinary crit, inherited by children
     * @param effectiveFerocity finite nonnegative diagnostic value
     * @param modifierBreakdown copied sorted map of nonblank names to finite diagnostic values
     * @param rejectionReason nonnull optional rejection; empty means accepted, including zero damage
     */
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

    /**
     * Returns true when no rejection reason is present; an accepted zero hit still consumes a commit ordinal.
     * @return true when no rejection reason is present, including accepted zero-damage results
     */
    public boolean accepted() { return rejectionReason.isEmpty(); }
}
