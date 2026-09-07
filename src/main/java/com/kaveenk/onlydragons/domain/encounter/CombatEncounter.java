package com.kaveenk.onlydragons.domain.encounter;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static com.kaveenk.onlydragons.domain.combat.DamageResult.RejectionReason.*;

/**
 * One target/generation and one owner thread (construct on the server thread in Paper).
 * The adapter settles physical validity/cancellation before calling physical; this service
 * cannot prove collision or suppress native damage. End on reset/shutdown and discard the
 * instance; use a fresh encounter UUID next time. Only immutable snapshots cross threads.
 * <p>
 * All instance APIs enforce creating-thread ownership with IllegalStateException. Accepted
 * records, claims and stamps are retained for the entire generation; recentImpacts is only
 * a projection, not pruning. Numeric/provenance validation precedes each atomic commit.
 * @see com.kaveenk.onlydragons.application.proc.ProcCoordinator
 * @see com.kaveenk.onlydragons.application.fire.OwnedFireCoordinator
 */
public final class CombatEncounter {
    /**
     * Creating thread shared with the proc coordinator; Paper constructs on its server thread.
     */
    private final Thread ownerThread = Thread.currentThread();
    private final CombatProfile profile;
    private final String variantId;
    /**
     * Optional full immutable content provenance, retained even when catalog labels are later reused.
     */
    private final Optional<com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog.Selection> selection;
    /**
     * Encounter-global successful-commit sequence; accepted zeros also consume an ordinal.
     */
    private long acceptedOrdinal;
    /**
     * Last accepted receiver tick, initially −1; rejects backdated successful commits.
     */
    private long lastCommitTick = -1;
    /**
     * Accepted impact ID to immutable tick/ordinal, retained until the encounter is discarded.
     */
    private final Map<UUID, EncounterResult.CommitStamp> stamps = new LinkedHashMap<>();
    private final DamageCalculator calculator = new DamageCalculator();
    /**
     * Captured Flame levels for accepted physical sources, used to reject forged fire commands.
     */
    private final Map<UUID, Integer> flameLevels = new LinkedHashMap<>();
    /**
     * Full pre-hit HP policy bound to each accepted physical parent; never recomputed at drain.
     */
    private final Map<UUID, ProcHealthSnapshot> procPolicies = new LinkedHashMap<>();
    /**
     * Authoritative accepted results in commit order; also validates virtual parent equality and IDs.
     */
    private final Map<UUID, DamageResult> accepted = new LinkedHashMap<>();
    /**
     * Generation-local physical-key deduplication; unsuccessful candidates never reserve a key.
     */
    private final Map<PhysicalImpact.Key, UUID> physicalClaims = new LinkedHashMap<>();
    /**
     * Cumulative full-precision HP/credit and provenance by owner UUID, independent of login.
     */
    private final Map<UUID, EncounterResult.Contribution> contributions = new TreeMap<>();
    /**
     * Current immutable domain HP projection replaced at each commit.
     */
    private TargetState target;
    /**
     * Null before a lethal commit; thereafter the single retained immutable completion.
     */
    private EncounterResult completion;
    /**
     * Terminal admission flag; setting it neither clears diagnostic state nor mints a defeat.
     */
    private boolean ended;

    /**
     * Creates a full-health generation without catalog provenance. Null inputs, blank variant or
     * a target that is not at full positive HP reject; retain this instance only for this generation.
     */
    public CombatEncounter(TargetState target, String variantId, CombatProfile profile) {
        this(target, variantId, profile, Optional.empty());
    }

    /**
     * Creates a generation on the calling thread. Optional full catalog selection must match
     * variant ID, complete combat profile, maximum HP and defense exactly. No native entity is spawned.
     * @param target nonnull initial full positive domain HP
     * @param variantId nonblank type identity
     * @param profile nonnull immutable encounter policy
     * @param selection nonnull optional retained full catalog content
     */
    public CombatEncounter(TargetState target, String variantId, CombatProfile profile,
                           Optional<com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog.Selection> selection) {
        this.target = Objects.requireNonNull(target, "target");
        if (!target.alive() || target.currentHealth() != target.maxHealth()) {
            throw new IllegalArgumentException("A new encounter must start at full positive health");
        }
        this.variantId = DomainChecks.text(variantId, "variantId");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.selection = Objects.requireNonNull(selection);
        selection.ifPresent(value -> {
            if (!value.identity().id().equals(variantId) || !value.combatProfile().equals(profile)
                    || value.maxHealth() != target.maxHealth() || value.defense() != target.defense())
                throw new IllegalArgumentException("Selection does not match encounter");
        });
    }

    /**
     * Returns the current immutable domain HP value; a retained older value never follows later commits.
     */
    public TargetState target() { checkThread(); return target; }
    /**
     * Returns the frozen lethal completion, or empty before defeat and after nonlethal end.
     * Ending a defeated encounter does not erase its existing completion.
     */
    public Optional<EncounterResult> completion() { checkThread(); return Optional.ofNullable(completion); }
    /**
     * Returns an immutable copy of every accepted hit in commit order, including accepted zeros.
     */
    public List<DamageResult> impacts() { checkThread(); return List.copyOf(accepted.values()); }
    /**
     * Bounded diagnostic projection; authoritative idempotency/accounting remains intact.
     * <p>
     * Returns the newest limit accepted hits in their original commit order; zero returns empty.
     * A negative limit rejects. This call does not remove claims, parent data or history.
     */
    public List<DamageResult> recentImpacts(int limit) {
        checkThread(); if (limit < 0) throw new IllegalArgumentException("Negative impact limit");
        return accepted.values().stream().skip(Math.max(0, accepted.size() - limit)).toList();
    }
    /**
     * Returns an immutable full-precision totals snapshot; map iteration order is unspecified.
     */
    public Map<UUID, EncounterResult.Contribution> contributions() { checkThread(); return Map.copyOf(contributions); }
    /**
     * Idempotently closes this generation to new accepted hits, preserving HP, claims and completion.
     * No defeat is manufactured and no native resources are released; the adapter owns their cleanup.
     */
    public void end() { checkThread(); ended = true; }
    /**
     * Returns the total successful commits, starting at zero and increasing for accepted zero damage too.
     */
    public long acceptedOrdinal() { checkThread(); return acceptedOrdinal; }
    /**
     * Returns the accepted commit stamp for an impact ID, or empty for unknown/rejected IDs.
     * No stamp is minted by inspection.
     */
    public Optional<EncounterResult.CommitStamp> stamp(UUID impact) { checkThread(); return Optional.ofNullable(stamps.get(impact)); }

    /**
     * Convenience physical entry with no active pre-hit Tempo. Delegates all acceptance and commit
     * checks to the overload with explicit state; it does not sample live buffs.
     */
    public DamageResult physical(ShotContext shot, PhysicalImpact impact, DamageModifiers modifiers,
                                 double effectiveFerocity, Optional<DamageResult.RejectionReason> adapterRejection) {
        return physical(shot, impact, modifiers, effectiveFerocity, adapterRejection,
                new com.kaveenk.onlydragons.domain.enchant.TempoState(0, 0));
    }

    /**
     * Coordinator supplies the pre-hit shared state; commit binds it to this physical identity.
     * <p>
     * Commits one settled candidate after generation/target/owner/veto/duplicate checks. Same-tick
     * candidates commit in caller order; the first lethal freezes completion. Malformed policy,
     * expired active Tempo, backdated timing and arithmetic failure throw before ledger mutation.
     * Rejected candidates return zero HP/credit without reserving the physical key.
     * @param shot nonnull immutable launch inputs matching the encounter policy
     * @param impact nonnull settled candidate with receiver tick and real collision position
     * @param modifiers nonnull physical modifiers to apply once
     * @param effectiveFerocity finite nonnegative pre-hit diagnostic Ferocity
     * @param adapterRejection nonnull optional settled external rejection
     * @param activeTempo nonnull already-expired-or-live pre-hit state from the coordinator
     * @return immutable authoritative accepted or rejected result
     */
    public DamageResult physical(ShotContext shot, PhysicalImpact impact, DamageModifiers modifiers,
                                 double effectiveFerocity, Optional<DamageResult.RejectionReason> adapterRejection,
                                 com.kaveenk.onlydragons.domain.enchant.TempoState activeTempo) {
        checkThread();
        Objects.requireNonNull(activeTempo);
        if (activeTempo.bonusPercent() > 0 && activeTempo.expiresAt() <= impact.tick())
            throw new IllegalArgumentException("Pre-hit Tempo must already be expired");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(adapterRejection, "adapterRejection");
        DomainChecks.nonNegative(effectiveFerocity, "effectiveFerocity");
        if (!profile.mechanic().equals(shot.mechanic())) throw new IllegalArgumentException("Shot profile mismatch");
        UUID id = physicalId(impact.key());
        var kind = shot.parentProjectileId().isPresent() ? DamageResult.Kind.DUPLEX : DamageResult.Kind.PHYSICAL;
        var reason = boundary(impact.key());
        if (reason.isEmpty() && !shot.encounterId().equals(target.encounterId())) reason = Optional.of(WRONG_ENCOUNTER);
        if (reason.isEmpty() && (!shot.ownerId().equals(impact.ownerId())
                || !shot.projectileId().equals(impact.key().projectileId()))) reason = Optional.of(UNOWNED);
        if (reason.isEmpty()) reason = adapterRejection;
        if (reason.isEmpty() && (physicalClaims.containsKey(impact.key()) || accepted.containsKey(id))) reason = Optional.of(DUPLICATE_IMPACT);
        if (reason.isPresent()) return rejected(id, Optional.empty(), impact.key(), shot.ownerId(), shot.shotId(),
                kind, impact.tick(), shot.crit(), effectiveFerocity, reason.get());
        if (impact.tick() < shot.launchTick()) throw new IllegalArgumentException("Impact precedes launch");
        int flame = com.kaveenk.onlydragons.domain.enchant.EnchantEffects.level(shot.enchantments(), "flame", 2);
        var calculation = calculator.physical(shot, modifiers, target, profile);
        var policy = new ProcHealthSnapshot(activeTempo.bonusPercent(), activeTempo.sourceLevel(),
                activeTempo.bonusPercent() == 0 ? 0 : activeTempo.expiresAt(),
                profile.ferocityHealthFraction(activeTempo.sourceLevel()), calculation.cappedDamage());
        var result = commit(id, Optional.empty(), impact.key(), shot.ownerId(), shot.shotId(), kind,
                impact.tick(), shot.crit(), effectiveFerocity, calculation, 1);
        physicalClaims.put(impact.key(), id);
        procPolicies.put(id, policy);
        if (flame > 0) flameLevels.put(id, flame);
        return result;
    }

    /**
     * T05 owns scheduling/counts. A child can only consume a matching accepted physical parent.
     * <p>
     * Checks the due tick and exact parent origin, owner, shot, crit, policy and mitigated basis.
     * The captured HP snapshot must match the bound parent; legacy absent snapshots require FIXED.
     * A virtual child cannot parent another child. Lifecycle/duplicate rejection returns zero credit;
     * malformed or premature commands throw before mutation. Count authorization remains external.
     * @param command nonnull admitted command
     * @param tick nonnegative receiver tick at or after due time for a live accepted child
     * @return immutable result; callers must not apply its damage again
     */
    public DamageResult proc(ProcCommand command, long tick) {
        checkThread();
        DomainChecks.nonNegative(tick, "tick");
        if (!profile.mechanic().equals(command.mechanic())) throw new IllegalArgumentException("Proc profile mismatch");
        var reason = boundary(command.origin());
        if (reason.isEmpty() && accepted.containsKey(command.procId())) reason = Optional.of(DUPLICATE_IMPACT);
        var parent = accepted.get(command.parentImpactId());
        if (reason.isPresent()) return rejected(command.procId(), Optional.of(command.parentImpactId()), command.origin(),
                command.ownerId(), command.shotId(), DamageResult.Kind.FEROCITY, tick, command.crit(), 0, reason.get());
        if (tick < command.dueTick()) throw new IllegalArgumentException("Proc is not due");
        if (parent == null || (parent.kind() != DamageResult.Kind.PHYSICAL && parent.kind() != DamageResult.Kind.DUPLEX)
                || !parent.origin().equals(command.origin()) || !parent.ownerId().equals(command.ownerId())
                || !parent.shotId().equals(command.shotId()) || parent.crit() != command.crit()
                || parent.amounts().mitigatedDamage() != command.preCapDamage()
                || command.dueTick() < parent.tick()) {
            throw new IllegalArgumentException("Proc does not match an accepted physical parent");
        }
        return commit(command.procId(), Optional.of(command.parentImpactId()), command.origin(), command.ownerId(),
                command.shotId(), DamageResult.Kind.FEROCITY, tick, command.crit(), parent.effectiveFerocity(),
                calculator.proc(parent, target, profile), procFraction(command, parent));
    }

    /**
     * Full HP/full credit, already-resolved physical credit basis; no mitigation or offensive reroll.
     * <p>
     * Verifies exact accepted source and its captured Flame level, then computes physical credit
     * × Flame fraction × sampled vulnerability and applies one cap. This path never builds Tempo
     * or recursively admits effects. Lifecycle/duplicate returns zero credit; mismatched or early
     * commands throw. Scheduling/count admission belongs to the fire coordinator.
     * @param command nonnull admitted due strike
     * @param tick nonnegative evaluation game tick
     * @return authoritative immutable strike result, with actual HP clipped independently of credit
     */
    public DamageResult fire(FireCommand command, long tick) {
        checkThread();
        var source = command.source();
        if (!profile.mechanic().equals(source.mechanic())) throw new IllegalArgumentException("Fire profile mismatch");
        var reason = boundary(source.origin());
        if (reason.isEmpty() && accepted.containsKey(command.id())) reason = Optional.of(DUPLICATE_IMPACT);
        if (reason.isPresent()) return rejected(command.id(), Optional.of(source.impactId()), source.origin(),
                source.ownerId(), source.shotId(), DamageResult.Kind.FIRE, tick, source.crit(), 0, reason.get());
        if (tick < command.dueTick() || !source.equals(accepted.get(source.impactId()))
                || !Objects.equals(flameLevels.get(source.impactId()), command.level()))
            throw new IllegalArgumentException("Fire does not match its captured accepted source");
        double fraction = com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile.fireFraction(command.level());
        double potency = DomainChecks.nonNegative(source.amounts().contributionDamage() * fraction, "fire potency");
        double damage = DomainChecks.nonNegative(potency * command.vulnerability(), "fire damage");
        var calculation = new DamageCalculator.Calculation(damage, damage, profile.cap(damage, target.maxHealth()),
                Map.of("fire/quiver-flame-v1/level", (double) command.level(),
                        "fire/physicalCredit", source.amounts().contributionDamage(), "fire/fraction", fraction,
                        "fire/potency", potency, "fire/vulnerability", command.vulnerability(), "fire/dueTick", (double) command.dueTick()));
        return commit(command.id(), Optional.of(source.impactId()), source.origin(), source.ownerId(), source.shotId(),
                DamageResult.Kind.FIRE, tick, source.crit(), 0, calculation, 1);
    }

    /**
     * Returns the full immutable HP policy bound to an accepted physical parent. Unknown IDs
     * throw IllegalArgumentException; consumers retain this exact value across later buff changes.
     */
    public ProcHealthSnapshot procHealthSnapshot(UUID parentImpactId) {
        checkThread();
        var policy = procPolicies.get(parentImpactId);
        if (policy == null) throw new IllegalArgumentException("No accepted physical parent policy");
        return policy;
    }

    /**
     * Selects fixed legacy policy only when allowed, otherwise verifies the entire captured parent
     * policy by equality before returning its HP fraction; does not read current live Tempo.
     */
    private double procFraction(ProcCommand command, DamageResult parent) {
        if (command.healthSnapshot().isEmpty()) {
            if (profile.ferocityHealthPolicy() != CombatProfile.FerocityHealthPolicy.FIXED)
                throw new IllegalArgumentException("Missing frozen proc HP policy");
            return profile.ferocityHealthFraction();
        }
        var snapshot = command.healthSnapshot().orElseThrow();
        if (!snapshot.equals(procPolicies.get(parent.impactId())))
            throw new IllegalArgumentException("Proc HP policy does not match accepted parent");
        return snapshot.healthFraction();
    }

    /**
     * Stable across duplicate delivery; part names and event source deliberately do not enter this key.
     * <p>
     * Derives a UTF-8 name UUID from encounter, projectile, target and impact ordinal. Pure and
     * thread-independent; neither tick nor part label can turn duplicate delivery into a new hit.
     */
    public static UUID physicalId(PhysicalImpact.Key key) {
        String value = "physical:" + key.encounterId() + ":" + key.projectileId() + ":" + key.targetId() + ":" + key.impactOrdinal();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Ordered rejection precedence: wrong encounter, wrong target, dead target, ended generation.
     * Thus a matching dead target returns TARGET_DEAD even if the generation has also ended.
     */
    private Optional<DamageResult.RejectionReason> boundary(PhysicalImpact.Key key) {
        if (!key.encounterId().equals(target.encounterId())) return Optional.of(WRONG_ENCOUNTER);
        if (!key.targetId().equals(target.targetId())) return Optional.of(INVALID_TARGET);
        if (!target.alive()) return Optional.of(TARGET_DEAD);
        if (ended) return Optional.of(ENCOUNTER_ENDED);
        return Optional.empty();
    }

    /**
     * Prepares validated result, totals, target and possible completion before publishing any field.
     * Nondecreasing ticks and strictly increasing ordinals order accepted zeros and rounded-away
     * additions too. Only a strict represented-credit increase changes the last-increase stamp;
     * lethal credit is not clipped to remaining HP. No external callback runs inside this transaction.
     */
    private DamageResult commit(UUID id, Optional<UUID> parent, PhysicalImpact.Key origin, UUID player, UUID shot,
                                DamageResult.Kind kind, long tick, CritOutcome crit, double ferocity,
                                DamageCalculator.Calculation calculation, double healthFraction) {
        if (tick < lastCommitTick) throw new IllegalArgumentException("Encounter commit tick cannot move backwards");
        long ordinal = Math.incrementExact(acceptedOrdinal);
        var stamp = new EncounterResult.CommitStamp(tick, ordinal);
        double requested = calculation.cappedDamage() * healthFraction;
        double actual = Math.min(target.currentHealth(), requested);
        double score = calculation.cappedDamage();
        var result = new DamageResult(id, parent, origin, player, shot, kind, tick, profile.mechanic(),
                new DamageResult.Amounts(calculation.rawOffense(), calculation.mitigatedDamage(), score, requested, actual, score),
                crit, ferocity, calculation.breakdown(), Optional.empty());
        var previous = contributions.getOrDefault(player, new EncounterResult.Contribution(0, 0, 0, false));
        // Constructors reject non-finite accumulated totals before committing anything.
        var total = new EncounterResult.Contribution(previous.actualHealthDamage() + actual,
                previous.contributionDamage() + score, 0, true,
                previous.firstParticipation().or(() -> Optional.of(stamp)),
                previous.contributionDamage() + score > previous.contributionDamage()
                        ? Optional.of(stamp) : previous.lastCreditIncrease());
        var next = new TargetState(target.encounterId(), target.targetId(), target.maxHealth(), target.currentHealth() - actual, target.defense());
        EncounterResult finished = null;
        if (!next.alive()) {
            var finalTotals = new TreeMap<>(contributions);
            finalTotals.put(player, total);
            finished = new EncounterResult(id, target.encounterId(), variantId, profile.mechanic(), tick, finalTotals, ordinal, selection);
        }
        target = next;
        contributions.put(player, total);
        accepted.put(id, result);
        stamps.put(id, stamp);
        acceptedOrdinal = ordinal;
        lastCommitTick = tick;
        if (finished != null) completion = finished;
        return result;
    }

    /**
     * Builds a zero-amount diagnostic result without storing a claim, stamp, participant or history entry.
     */
    private DamageResult rejected(UUID id, Optional<UUID> parent, PhysicalImpact.Key origin, UUID player, UUID shot,
                                  DamageResult.Kind kind, long tick, CritOutcome crit, double ferocity,
                                  DamageResult.RejectionReason reason) {
        return new DamageResult(id, parent, origin, player, shot, kind, tick, profile.mechanic(),
                new DamageResult.Amounts(0, 0, 0, 0, 0, 0), crit, ferocity, Map.of(), Optional.of(reason));
    }

    /**
     * Rejects access from any thread other than the constructing thread; this is confinement, not locking.
     */
    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Combat encounter accessed outside its owner thread");
    }
}
