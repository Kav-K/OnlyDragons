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
 */
public final class CombatEncounter {
    private final Thread ownerThread = Thread.currentThread();
    private final CombatProfile profile;
    private final String variantId;
    private final Optional<com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalog.Selection> selection;
    private long acceptedOrdinal;
    private long lastCommitTick = -1;
    private final Map<UUID, EncounterResult.CommitStamp> stamps = new LinkedHashMap<>();
    private final DamageCalculator calculator = new DamageCalculator();
    private final Map<UUID, DamageResult> accepted = new LinkedHashMap<>();
    private final Map<PhysicalImpact.Key, UUID> physicalClaims = new LinkedHashMap<>();
    private final Map<UUID, EncounterResult.Contribution> contributions = new TreeMap<>();
    private TargetState target;
    private EncounterResult completion;
    private boolean ended;

    public CombatEncounter(TargetState target, String variantId, CombatProfile profile) {
        this(target, variantId, profile, Optional.empty());
    }

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

    public TargetState target() { checkThread(); return target; }
    public Optional<EncounterResult> completion() { checkThread(); return Optional.ofNullable(completion); }
    public List<DamageResult> impacts() { checkThread(); return List.copyOf(accepted.values()); }
    /** Bounded diagnostic projection; authoritative idempotency/accounting remains intact. */
    public List<DamageResult> recentImpacts(int limit) {
        checkThread(); if (limit < 0) throw new IllegalArgumentException("Negative impact limit");
        return accepted.values().stream().skip(Math.max(0, accepted.size() - limit)).toList();
    }
    public Map<UUID, EncounterResult.Contribution> contributions() { checkThread(); return Map.copyOf(contributions); }
    public void end() { checkThread(); ended = true; }
    public long acceptedOrdinal() { checkThread(); return acceptedOrdinal; }
    public Optional<EncounterResult.CommitStamp> stamp(UUID impact) { checkThread(); return Optional.ofNullable(stamps.get(impact)); }

    public DamageResult physical(ShotContext shot, PhysicalImpact impact, DamageModifiers modifiers,
                                 double effectiveFerocity, Optional<DamageResult.RejectionReason> adapterRejection) {
        checkThread();
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
        var calculation = calculator.physical(shot, modifiers, target, profile);
        var result = commit(id, Optional.empty(), impact.key(), shot.ownerId(), shot.shotId(), kind,
                impact.tick(), shot.crit(), effectiveFerocity, calculation);
        physicalClaims.put(impact.key(), id);
        return result;
    }

    /** T05 owns scheduling/counts. A child can only consume a matching accepted physical parent. */
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
        if (parent == null || parent.kind() == DamageResult.Kind.FEROCITY
                || !parent.origin().equals(command.origin()) || !parent.ownerId().equals(command.ownerId())
                || !parent.shotId().equals(command.shotId()) || parent.crit() != command.crit()
                || parent.amounts().mitigatedDamage() != command.preCapDamage()
                || command.dueTick() < parent.tick()) {
            throw new IllegalArgumentException("Proc does not match an accepted physical parent");
        }
        return commit(command.procId(), Optional.of(command.parentImpactId()), command.origin(), command.ownerId(),
                command.shotId(), DamageResult.Kind.FEROCITY, tick, command.crit(), parent.effectiveFerocity(),
                calculator.proc(parent, target, profile));
    }

    /** Stable across duplicate delivery; part names and event source deliberately do not enter this key. */
    public static UUID physicalId(PhysicalImpact.Key key) {
        String value = "physical:" + key.encounterId() + ":" + key.projectileId() + ":" + key.targetId() + ":" + key.impactOrdinal();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<DamageResult.RejectionReason> boundary(PhysicalImpact.Key key) {
        if (!key.encounterId().equals(target.encounterId())) return Optional.of(WRONG_ENCOUNTER);
        if (!key.targetId().equals(target.targetId())) return Optional.of(INVALID_TARGET);
        if (!target.alive()) return Optional.of(TARGET_DEAD);
        if (ended) return Optional.of(ENCOUNTER_ENDED);
        return Optional.empty();
    }

    private DamageResult commit(UUID id, Optional<UUID> parent, PhysicalImpact.Key origin, UUID player, UUID shot,
                                DamageResult.Kind kind, long tick, CritOutcome crit, double ferocity,
                                DamageCalculator.Calculation calculation) {
        if (tick < lastCommitTick) throw new IllegalArgumentException("Encounter commit tick cannot move backwards");
        long ordinal = Math.incrementExact(acceptedOrdinal);
        var stamp = new EncounterResult.CommitStamp(tick, ordinal);
        double requested = calculation.cappedDamage() * (kind == DamageResult.Kind.FEROCITY ? profile.ferocityHealthFraction() : 1);
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

    private DamageResult rejected(UUID id, Optional<UUID> parent, PhysicalImpact.Key origin, UUID player, UUID shot,
                                  DamageResult.Kind kind, long tick, CritOutcome crit, double ferocity,
                                  DamageResult.RejectionReason reason) {
        return new DamageResult(id, parent, origin, player, shot, kind, tick, profile.mechanic(),
                new DamageResult.Amounts(0, 0, 0, 0, 0, 0), crit, ferocity, Map.of(), Optional.of(reason));
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Combat encounter accessed outside its owner thread");
    }
}
