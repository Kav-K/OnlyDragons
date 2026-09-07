package com.kaveenk.onlydragons.domain.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.domain.combat.DamageResult.RejectionReason.*;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Deterministic damage/ledger oracles: 210 critical offense, historical raw-input cap bands,
 * pre-cap proc inheritance, independent overkill/ghost credit and atomic validation failures.
 * Fixed identities/ticks and injected threshold samples isolate domain behavior; direct physical
 * candidates do not establish native collision or suppression. A separate thread tests confinement.
 */
class CombatServicesTest {
    private static final UUID ENCOUNTER = new UUID(0, 1), OWNER = new UUID(0, 2), TARGET = new UUID(0, 3);
    private static final CombatProfile CALIBRATION = CombatProfile.calibration();
    private static final DamageModifiers POWER = new DamageModifiers(Map.of("power", 0.4), Map.of());

    @Test void goldenOffenseUsesCapturedCritDrawAndSeparateModifiers() {
        var calculator = new DamageCalculator();
        var target = target(1000, 0);
        var crit = shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty());
        assertEquals(210, calculator.physical(crit, POWER, target, CALIBRATION).rawOffense(), 1e-10);
        var normal = shot(5, 100, CritOutcome.NORMAL, CALIBRATION, 0.5, 0.2, Optional.empty());
        var calculated = calculator.physical(normal, new DamageModifiers(Map.of("power", 0.4, "snipe", 0.1),
                Map.of("separate", 2.0)), target(1000, 100), CALIBRATION);
        assertEquals(30, calculated.rawOffense(), 1e-10);
        assertEquals(15, calculated.mitigatedDamage(), 1e-10);
        assertEquals(15, calculated.cappedDamage(), 1e-10);
        assertEquals(0.4, calculated.breakdown().get("additive/power"));
        assertEquals(1, calculated.breakdown().get("criticalMultiplier"));
    }

    @Test void critProbabilityUsesStrictThresholdAndRetainsRawAbove100() {
        var resolver = new CritResolver();
        assertEquals(CritOutcome.NORMAL, resolver.roll(stats(100, 0), () -> 0));
        assertEquals(CritOutcome.CRITICAL, resolver.roll(stats(100, 100), () -> Math.nextDown(1.0)));
        assertEquals(CritOutcome.CRITICAL, resolver.roll(stats(100, 25), () -> Math.nextDown(0.25)));
        assertEquals(CritOutcome.NORMAL, resolver.roll(stats(100, 25), () -> 0.25));
        var above = stats(100, 175);
        assertEquals(175, above.raw(StatKey.CRIT_CHANCE));
        assertEquals(CritOutcome.CRITICAL, resolver.roll(above, () -> 0.99));
        for (double invalid : new double[]{-0.1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> resolver.roll(above, () -> invalid));
        }
    }

    @ParameterizedTest @CsvSource({"4000,4000,1,0.1", "24000,6000,0.1,0.01", "224000,8000,0.01,0.001", "2224000,10000,0.001,0"})
    void historicalCapBoundariesAreContinuousAndUseRawInputBands(double input, double output, double before, double after) {
        var p = dragon(0.25);
        assertEquals(output - before, p.cap(input - 1, 1_000_000), 1e-8);
        assertEquals(output, p.cap(input, 1_000_000), 1e-8);
        assertEquals(output + after, p.cap(input + 1, 1_000_000), 1e-8);
    }

    @Test void capZeroMonotonicBoundAndExtremeFiniteInputs() {
        var p = dragon(1);
        assertEquals(0, p.cap(0, 1_000_000));
        double previous = 0;
        for (int input = 0; input <= 3_000_000; input += 137) {
            double current = p.cap(input, 1_000_000);
            assertTrue(current >= previous && current <= 10_000);
            previous = current;
        }
        assertEquals(10_000, p.cap(Double.MAX_VALUE, 1_000_000));
        assertTrue(Double.isFinite(p.cap(Double.MAX_VALUE, Double.MAX_VALUE)));
        assertEquals(Double.MAX_VALUE, CALIBRATION.cap(Double.MAX_VALUE, 1000));
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> p.cap(invalid, 100));
            assertThrows(IllegalArgumentException.class, () -> p.mitigate(100, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> p.cap(1, 0));
        assertThrows(IllegalArgumentException.class, () -> dragon(1.1));
    }

    @Test void mitigationCanBeDisabledAndCannotOverflow() {
        var noDefense = new CombatProfile(CALIBRATION.mechanic(), CombatProfile.Mitigation.NONE, CombatProfile.Cap.NONE, 1);
        assertEquals(100, noDefense.mitigate(100, 100));
        assertEquals(50, CALIBRATION.mitigate(100, 100));
        assertTrue(Double.isFinite(CALIBRATION.mitigate(Double.MAX_VALUE, Double.MAX_VALUE)));
    }

    @Test void modifiersAreImmutableCanonicalAndInvalidArithmeticIsRejected() {
        var source = new HashMap<>(Map.of("b", 0.1, "a", 0.4));
        var modifiers = new DamageModifiers(source, Map.of());
        source.clear();
        assertEquals(List.of("a", "b"), List.copyOf(modifiers.additiveFractions().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> modifiers.additiveFractions().clear());
        assertThrows(IllegalArgumentException.class, () -> new DamageModifiers(Map.of("bad", Double.NaN), Map.of()));
        var session = encounter(1000, 0, CALIBRATION);
        var shot = shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty());
        var overflow = new DamageModifiers(Map.of("a", Double.MAX_VALUE, "b", Double.MAX_VALUE), Map.of());
        assertThrows(IllegalArgumentException.class, () -> hit(session, shot, overflow));
        assertEquals(1000, session.target().currentHealth());
        assertTrue(session.impacts().isEmpty());
        assertTrue(hit(session, shot, POWER).accepted()); // failed calculation did not consume the key
    }

    @Test void repeatedPhysicalEventsAndRepeatedChildrenCannotDoubleCount() {
        var session = encounter(1000, 0, CALIBRATION);
        var shot = shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty());
        var first = hit(session, shot, POWER);
        assertRejected(DUPLICATE_IMPACT, hit(session, shot, POWER));
        var child = child(first, 8, first.amounts().mitigatedDamage(), first.crit());
        assertTrue(session.proc(child, 22).accepted());
        assertRejected(DUPLICATE_IMPACT, session.proc(child, 22));
        assertEquals(580, session.target().currentHealth());
        assertEquals(420, session.contributions().get(OWNER).contributionDamage());
        assertEquals(2, session.impacts().size());
    }

    @Test void lethalCandidatesFreezeOneResultAndLateDescendantsEarnNothing() {
        var profile = new CombatProfile(new MechanicRevision("reduced-test-calibration", "v1"),
                CombatProfile.Mitigation.NONNEGATIVE_DEFENSE, CombatProfile.Cap.NONE, 0.25);
        var session = encounter(230, 0, profile);
        var first = hit(session, shot(4, 100, CritOutcome.CRITICAL, profile, 1, 1, Optional.empty()), POWER);
        var child = session.proc(child(first, 8, 210, CritOutcome.CRITICAL), 22);
        assertEquals(52.5, child.amounts().requestedHealthDamage());
        assertEquals(20, child.amounts().actualHealthDamage());
        assertEquals(210, child.amounts().contributionDamage());
        assertEquals(0, session.target().currentHealth());
        var result = session.completion().orElseThrow();
        assertEquals(child.impactId(), result.completionId());
        assertRejected(TARGET_DEAD, session.proc(child(first, 9, 210, CritOutcome.CRITICAL), 22));
        assertRejected(TARGET_DEAD, hit(session, shot(5, 100, CritOutcome.CRITICAL, profile, 1, 1, Optional.empty()), POWER));
        assertSame(result, session.completion().orElseThrow());
        assertEquals(new EncounterResult.Contribution(230, 420, 0, true, Optional.of(new EncounterResult.CommitStamp(20, 1)), Optional.of(new EncounterResult.CommitStamp(22, 2))), result.participants().get(OWNER));
        assertEquals(2, session.impacts().size());
        assertThrows(UnsupportedOperationException.class, () -> result.participants().clear());
    }

    @Test void simultaneousPhysicalLethalsCommitInCallerOrderAndOnlyOnce() {
        var session = encounter(100, 0, CALIBRATION);
        var first = hit(session, shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty()), POWER);
        var second = hit(session, shot(5, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty()), POWER);
        assertEquals(100, first.amounts().actualHealthDamage());
        assertEquals(210, first.amounts().contributionDamage());
        assertRejected(TARGET_DEAD, second);
        assertEquals(first.impactId(), session.completion().orElseThrow().completionId());
    }

    @Test void procInheritsMitigatedBasisAndCapIsAppliedOncePerHit() {
        var profile = dragon(0.25);
        var session = encounter(1_000_000, 100, profile);
        var first = hit(session, shot(4, 48_000, CritOutcome.NORMAL, profile, 1, 1, Optional.empty()), DamageModifiers.none());
        assertEquals(24_000, first.amounts().mitigatedDamage());
        assertEquals(6000, first.amounts().cappedDamage(), 1e-9);
        var proc = session.proc(child(first, 8, 24_000, CritOutcome.NORMAL), 22);
        assertEquals(6000, proc.amounts().cappedDamage(), 1e-9);
        assertEquals(1500, proc.amounts().actualHealthDamage(), 1e-9);
        assertEquals(992_500, session.target().currentHealth(), 1e-9);
        assertEquals(12_000, session.contributions().get(OWNER).contributionDamage(), 1e-9);
    }

    @Test void scoreOnlyProcsDoNotKillAndDuplexBasisStaysScaled() {
        var profile = new CombatProfile(new MechanicRevision("score-only-test-calibration", "v1"),
                CombatProfile.Mitigation.NONNEGATIVE_DEFENSE, CombatProfile.Cap.NONE, 0);
        var session = encounter(1000, 0, profile);
        var first = hit(session, shot(4, 100, CritOutcome.CRITICAL, profile, 1, 0.2, Optional.of(new UUID(0, 7))), POWER);
        assertEquals(DamageResult.Kind.DUPLEX, first.kind());
        assertEquals(42, first.amounts().cappedDamage(), 1e-10);
        var proc = session.proc(child(first, 8, first.amounts().mitigatedDamage(), CritOutcome.CRITICAL), 22);
        assertEquals(0, proc.amounts().actualHealthDamage());
        assertEquals(42, proc.amounts().contributionDamage(), 1e-10);
        assertEquals(958, session.target().currentHealth(), 1e-10);
        assertTrue(session.completion().isEmpty());
    }

    @Test void invalidEarlyForgedAndRecursiveChildrenCannotChangeLedger() {
        var session = encounter(1000, 0, CALIBRATION);
        var first = hit(session, shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty()), POWER);
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(first, 8, 210, CritOutcome.CRITICAL), 21));
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(first, 8, 420, CritOutcome.CRITICAL), 22));
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(first, 8, 210, CritOutcome.NORMAL), 22));
        var child = session.proc(child(first, 8, 210, CritOutcome.CRITICAL), 22);
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(child, 9, 210, CritOutcome.CRITICAL), 22));
        assertEquals(2, session.impacts().size());
        assertEquals(580, session.target().currentHealth());
    }

    @Test void cancellationOwnershipTargetGenerationAndEndBoundariesRejectWithoutCredit() {
        var session = encounter(1000, 0, CALIBRATION);
        var shot = shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty());
        var impact = impact(shot);
        assertRejected(CANCELLED, session.physical(shot, impact, POWER, 25, Optional.of(CANCELLED)));
        var wrongOwner = new PhysicalImpact(impact.key(), UUID.randomUUID(), 20, impact.position(), Optional.empty());
        assertRejected(UNOWNED, session.physical(shot, wrongOwner, POWER, 25, Optional.empty()));
        var wrongTarget = new PhysicalImpact(new PhysicalImpact.Key(ENCOUNTER, shot.projectileId(), UUID.randomUUID(), 0), OWNER, 20, impact.position(), Optional.empty());
        assertRejected(INVALID_TARGET, session.physical(shot, wrongTarget, POWER, 25, Optional.empty()));
        var wrongEncounter = new PhysicalImpact(new PhysicalImpact.Key(UUID.randomUUID(), shot.projectileId(), TARGET, 0), OWNER, 20, impact.position(), Optional.empty());
        assertRejected(WRONG_ENCOUNTER, session.physical(shot, wrongEncounter, POWER, 25, Optional.empty()));
        var first = hit(session, shot, POWER);
        var frozen = session.contributions();
        session.end(); session.end();
        assertRejected(ENCOUNTER_ENDED, session.proc(child(first, 8, 210, CritOutcome.CRITICAL), 22));
        assertRejected(ENCOUNTER_ENDED, hit(session, shot, POWER));
        assertEquals(frozen, session.contributions());
        assertTrue(session.completion().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> frozen.clear());
    }

    @Test void independentGenerationsKeepReusedPlayersAndOldSnapshotsSeparate() {
        var first = encounter(1000, 0, CALIBRATION);
        var old = first.target();
        var oldTotals = first.contributions();
        var shot = shot(4, 100, CritOutcome.CRITICAL, CALIBRATION, 1, 1, Optional.empty());
        hit(first, shot, POWER);
        var second = new CombatEncounter(new TargetState(new UUID(0, 99), TARGET, 1000, 1000, 0), "practice", CALIBRATION);
        assertRejected(WRONG_ENCOUNTER, hit(second, shot, POWER));
        assertEquals(1000, old.currentHealth());
        assertTrue(oldTotals.isEmpty());
        assertTrue(second.contributions().isEmpty());
    }

    @Test void encounterRejectsAccessFromAnotherThread() throws InterruptedException {
        var session = encounter(1000, 0, CALIBRATION);
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> { try { session.end(); } catch (Throwable thrown) { failure.set(thrown); } });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(hit(session, shot(4, 100, CritOutcome.NORMAL, CALIBRATION, 1, 1, Optional.empty()), POWER).accepted());
    }

    @Test void participantsHaveIndependentTotalsAndZeroDamageIsStillParticipation() {
        var session = encounter(300, 0, CALIBRATION);
        var first = shot(4, 100, CritOutcome.NORMAL, CALIBRATION, 1, 1, Optional.empty());
        hit(session, first, DamageModifiers.none());
        var other = new UUID(0, 33);
        var second = new ShotContext(first.encounterId(), new UUID(1, 5), new UUID(0, 5), 0, Optional.empty(), other,
                first.weapon(), stats(250, 0), List.of(), first.mechanic(), 10, first.launchPosition(), first.initialVelocity(),
                CritOutcome.NORMAL, 1, 1);
        var zero = new DamageModifiers(Map.of(), Map.of("zero", 0.0));
        var zeroShot = shot(6, 100, CritOutcome.NORMAL, CALIBRATION, 1, 1, Optional.empty());
        assertEquals(0, hit(session, zeroShot, zero).amounts().contributionDamage());
        var secondImpact = new PhysicalImpact(new PhysicalImpact.Key(ENCOUNTER, second.projectileId(), TARGET, 0),
                other, 20, first.launchPosition(), Optional.empty());
        session.physical(second, secondImpact, DamageModifiers.none(), 0, Optional.empty());
        var totals = session.completion().orElseThrow().participants();
        assertEquals(new EncounterResult.Contribution(100, 100, 0, true, Optional.of(new EncounterResult.CommitStamp(20, 1)), Optional.of(new EncounterResult.CommitStamp(20, 1))), totals.get(OWNER));
        assertEquals(new EncounterResult.Contribution(200, 250, 0, true, Optional.of(new EncounterResult.CommitStamp(20, 3)), Optional.of(new EncounterResult.CommitStamp(20, 3))), totals.get(other));
    }

    @Test void contributionOverflowCannotPartiallyCommitOrConsumeAChildId() {
        var profile = new CombatProfile(new MechanicRevision("overflow-test", "v1"),
                CombatProfile.Mitigation.NONE, CombatProfile.Cap.NONE, 0);
        var session = encounter(Double.MAX_VALUE, 0, profile);
        var parent = hit(session, shot(4, 1, CritOutcome.NORMAL, profile, 1, 1, Optional.empty()),
                new DamageModifiers(Map.of(), Map.of("large", 1e308)));
        var before = session.target();
        var totals = session.contributions();
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(parent, 8, 1e308, CritOutcome.NORMAL), 22));
        assertSame(before, session.target());
        assertEquals(totals, session.contributions());
        assertEquals(1, session.impacts().size());
        assertTrue(session.completion().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> session.proc(child(parent, 8, 1e308, CritOutcome.NORMAL), 22));
    }

    private static CombatProfile dragon(double fraction) {
        return CombatProfile.dragonExperiment(new MechanicRevision("historical-cap-test-calibration", "v1"), fraction);
    }
    private static TargetState target(double hp, double defense) { return new TargetState(ENCOUNTER, TARGET, hp, hp, defense); }
    private static CombatEncounter encounter(double hp, double defense, CombatProfile profile) { return new CombatEncounter(target(hp, defense), "practice", profile); }
    /**
     * Uses the bundled resolver with explicit damage/chance, retaining its baseline crit damage 50.
     */
    private static StatSnapshot stats(double damage, double chance) {
        return new StatResolver(StatProfile.calibration()).resolve("fixture-v1",
                Map.of(StatKey.WEAPON_DAMAGE, damage, StatKey.CRIT_CHANCE, chance), ModifierSources.empty()).snapshot();
    }
    /**
     * Creates distinguishable deterministic group/arrow/weapon UUIDs at launch tick 10; crit and
     * scales are supplied explicitly so this fixture isolates damage from random capture.
     */
    private static ShotContext shot(long id, double damage, CritOutcome crit, CombatProfile profile, double draw, double scale, Optional<UUID> parent) {
        return new ShotContext(ENCOUNTER, new UUID(1, id), new UUID(0, id), 0, parent, OWNER,
                new WeaponIdentity(new UUID(2, id), "test-bow", 1, "v1"), stats(damage, 100), List.of(),
                profile.mechanic(), 10, new Vector3(0, 100, 0), new Vector3(0, 1, 0), crit, draw, scale);
    }
    /**
     * Supplies a synthetic settled candidate at tick 20 and ten-block displacement; not collision proof.
     */
    private static PhysicalImpact impact(ShotContext shot) {
        return new PhysicalImpact(new PhysicalImpact.Key(shot.encounterId(), shot.projectileId(), TARGET, 0), OWNER, 20,
                new Vector3(0, 110, 0), Optional.empty());
    }
    private static DamageResult hit(CombatEncounter session, ShotContext shot, DamageModifiers modifiers) {
        return session.physical(shot, impact(shot), modifiers, 25, Optional.empty());
    }
    /**
     * Constructs a legacy fixed-policy child due at tick 22 with supplied basis/crit, including
     * intentional mismatches for rejection tests.
     */
    private static ProcCommand child(DamageResult parent, long id, double basis, CritOutcome crit) {
        return new ProcCommand(new UUID(0, id), parent.impactId(), parent.origin(), parent.ownerId(), parent.shotId(),
                22, basis, crit, parent.mechanic(), 0);
    }
    /**
     * Requires the exact rejection plus zero actual HP and credit; a reason alone is insufficient.
     */
    private static void assertRejected(DamageResult.RejectionReason reason, DamageResult result) {
        assertEquals(Optional.of(reason), result.rejectionReason());
        assertEquals(0, result.amounts().actualHealthDamage());
        assertEquals(0, result.amounts().contributionDamage());
    }
}
