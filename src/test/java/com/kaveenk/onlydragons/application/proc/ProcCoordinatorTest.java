package com.kaveenk.onlydragons.application.proc;

import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.application.proc.ProcCoordinator.Admission.*;

class ProcCoordinatorTest {
    private final UUID encounterId = UUID.randomUUID(), owner = UUID.randomUUID(), target = UUID.randomUUID();
    private final ProcCoordinator.Session session = new ProcCoordinator.Session(owner, UUID.randomUUID());
    private final CombatProfile profile = CombatProfile.calibration();
    private CombatEncounter encounter = new CombatEncounter(new TargetState(encounterId, target, 100_000, 100_000, 100), "test", profile);

    @ParameterizedTest @CsvSource({"0,0", "25,1", "100,1", "250,3", "500,5"})
    void productionQueueCountsAndStableNonRecursiveChildren(double ferocity, int expected) {
        var coordinator = coordinator(20, 20);
        var shot = shot(ferocity, 0, false);
        var result = hit(coordinator, shot, 10);
        assertEquals(expected, result.requestedChildren());
        assertEquals(expected, result.children().size());
        assertEquals(expected == 0 ? NO_CHILDREN : QUEUED, result.admission());
        assertEquals(expected, result.children().stream().map(ProcCommand::procId).distinct().count());
        for (int i = 0; i < expected; i++) {
            var child = result.children().get(i);
            assertEquals(ProcCoordinator.childId(result.damage().impactId(), i + 1), child.procId());
            assertEquals(12 + i * 2, child.dueTick());
            assertEquals(75, child.preCapDamage()); // 100 * 1.5 crit / 2 defense
            assertEquals(CritOutcome.CRITICAL, child.crit());
        }
        assertEquals(PHYSICAL_REJECTED, hit(coordinator, shot, 10).admission());
        assertEquals(expected, coordinator.metrics().queued());
        assertTrue(coordinator.tick(11).isEmpty());
        var children = coordinator.tick(20);
        assertEquals(expected, children.size());
        assertTrue(children.stream().allMatch(DamageResult::accepted));
        assertTrue(children.stream().allMatch(r -> r.parentImpactId().equals(Optional.of(result.damage().impactId()))));
        assertEquals(0, coordinator.metrics().queued());
        assertTrue(coordinator.tick(100).isEmpty());
        assertEquals(expected + 1, encounter.impacts().size());
    }

    @Test void wholeGroupCapacityRejectionIsObservableAndCannotBeRetried() {
        var coordinator = coordinator(2, 2);
        var shot = shot(250, 5, false);
        var rejected = hit(coordinator, shot, 10);
        assertTrue(rejected.damage().accepted());
        assertEquals(CAPACITY_REJECTED, rejected.admission());
        assertEquals(3, rejected.requestedChildren());
        assertEquals(3, coordinator.metrics().capacityRejectedChildren());
        assertEquals(0, coordinator.metrics().queued());
        assertEquals(50, coordinator.tempoBonus(session, 10)); // physical still qualifies
        assertEquals(PHYSICAL_REJECTED, hit(coordinator, shot, 10).admission());
        assertEquals(3, coordinator.metrics().capacityRejectedChildren());
        assertEquals(QUEUED, hit(coordinator, shot(100, 0, false), 11).admission());
        assertEquals(2, coordinator.metrics().queued()); // 150 ferocity at sample 0
    }

    @Test void drainBudgetCannotBeBypassedByRepeatedSameTickCalls() {
        var coordinator = coordinator(5, 2);
        hit(coordinator, shot(500, 0, false), 10);
        assertEquals(2, coordinator.tick(20).size());
        assertTrue(coordinator.tick(20).isEmpty());
        assertEquals(3, coordinator.metrics().queued());
        assertEquals(2, coordinator.tick(21).size());
        assertEquals(1, coordinator.tick(22).size());
        assertThrows(IllegalArgumentException.class, () -> coordinator.tick(21));
    }

    @Test void capturedTempoAndDuplexBowsShareBonusButOnlyEligibleAncestryRefreshes() {
        var coordinator = coordinator(20, 20);
        var tempoBow = shot(100, 5, false);
        var duplexBow = shot(25, 0, true);
        assertNotEquals(tempoBow.weapon().instanceId(), duplexBow.weapon().instanceId());
        var parent = hit(coordinator, tempoBow, 10);
        assertEquals(100, parent.damage().effectiveFerocity()); // before this hit's increment
        assertEquals(50, coordinator.tempoBonus(session, 10));
        var child = coordinator.tick(12).getFirst();
        assertEquals(75, child.amounts().mitigatedDamage());
        assertEquals(100, coordinator.tempoBonus(session, 12)); // eligible child, no descendants
        var swapped = hit(coordinator, duplexBow, 13);
        assertEquals(50, swapped.damage().effectiveFerocity());
        assertEquals(15, swapped.damage().amounts().mitigatedDamage()); // its captured .2 basis
        assertEquals(DamageResult.Kind.DUPLEX, swapped.damage().kind());
        assertEquals(15, coordinator.tick(15).getFirst().amounts().mitigatedDamage());
        assertEquals(100, coordinator.tempoBonus(session, 71));
        assertEquals(0, coordinator.tempoBonus(session, 72)); // Duplex/proc did not refresh expiry 12+60
        assertEquals(25, hit(coordinator, shot(25, 0, true), 72).damage().effectiveFerocity());
        assertEquals(100, tempoBow.stats().effective(StatKey.FEROCITY));
    }

    @Test void zeroBaseBuildsBonusButNeverManufacturesFerocityAndMixedLevelsShareCap() {
        var coordinator = coordinator(20, 20);
        for (int i = 0; i < 6; i++) assertEquals(NO_CHILDREN, hit(coordinator, shot(0, 5, false), 10 + i).admission());
        assertEquals(200, coordinator.tempoBonus(session, 15));
        assertEquals(0, coordinator.metrics().queued());
        assertEquals(75, hit(coordinator, shot(25, 1, false), 16).damage().effectiveFerocity());
        assertEquals(200, coordinator.tempoBonus(session, 16));
    }

    @Test void expiredBuffIsPurgedBeforeImpactAndInactiveArrowsCannotRecreateIt() {
        var coordinator = coordinator(20, 20);
        hit(coordinator, shot(0, 5, false), 10);
        assertEquals(25, hit(coordinator, shot(25, 5, false), 70).damage().effectiveFerocity());
        assertEquals(50, coordinator.tempoBonus(session, 70));
        coordinator.clearSession(session);
        assertEquals(0, coordinator.metrics().tempoStates());
        assertEquals(0, coordinator.metrics().queued());
        var offline = hit(coordinator, shot(100, 5, false), 71);
        assertTrue(offline.damage().accepted());
        assertEquals(INACTIVE_SESSION, offline.admission());
        assertEquals(1, coordinator.metrics().inactiveRejectedChildren());
        assertEquals(0, coordinator.metrics().tempoStates());
    }

    @Test void reconnectAndLateQuitUseGenerationTokensAndSessionCapacityIsBounded() {
        var coordinator = coordinator(20, 20);
        hit(coordinator, shot(100, 5, false), 10);
        var reconnected = new ProcCoordinator.Session(owner, UUID.randomUUID());
        assertTrue(coordinator.activate(reconnected));
        assertEquals(0, coordinator.tempoBonus(reconnected, 10));
        assertEquals(1, coordinator.metrics().clearedChildren());
        coordinator.clearSession(session);
        assertEquals(1, coordinator.metrics().sessions());
        assertEquals(INACTIVE_SESSION, hit(coordinator, shot(100, 5, false), 11).admission());
        assertFalse(coordinator.activate(new ProcCoordinator.Session(UUID.randomUUID(), UUID.randomUUID())));
        assertTrue(coordinator.tick(20).isEmpty());
    }

    @Test void cancelledAndDuplicateParentsCannotBuildTempoOrQueue() {
        var coordinator = coordinator(20, 20);
        var shot = shot(100, 5, false);
        var cancelled = coordinator.physical(shot, impact(shot, 10), DamageModifiers.none(), session,
                Optional.of(DamageResult.RejectionReason.CANCELLED));
        assertEquals(PHYSICAL_REJECTED, cancelled.admission());
        assertEquals(0, coordinator.tempoBonus(session, 10));
        hit(coordinator, shot, 10);
        hit(coordinator, shot, 10);
        assertEquals(50, coordinator.tempoBonus(session, 10));
        assertEquals(1, coordinator.metrics().queued());
    }

    @Test void deadOrEndedTargetRejectsDueChildrenWithoutScoreOrTempo() {
        encounter = new CombatEncounter(new TargetState(encounterId, target, 75, 75, 100), "test", profile);
        var coordinator = coordinator(20, 20);
        hit(coordinator, shot(500, 5, false), 10);
        var frozen = encounter.contributions();
        var children = coordinator.tick(20);
        assertEquals(5, children.size());
        assertTrue(children.stream().allMatch(r -> r.rejectionReason().equals(Optional.of(DamageResult.RejectionReason.TARGET_DEAD))));
        assertEquals(frozen, encounter.contributions());
        assertEquals(50, coordinator.tempoBonus(session, 20));
        assertEquals(0, coordinator.metrics().queued());
    }

    @Test void endResetAndCloseReleaseStateAndRejectLateCallbacks() {
        var coordinator = coordinator(20, 20);
        hit(coordinator, shot(100, 5, false), 10);
        encounter.end();
        assertEquals(Optional.of(DamageResult.RejectionReason.ENCOUNTER_ENDED), coordinator.tick(12).getFirst().rejectionReason());
        coordinator.close(); coordinator.close();
        assertEquals(0, coordinator.metrics().queued());
        assertEquals(0, coordinator.metrics().sessions());
        assertEquals(0, coordinator.metrics().tempoStates());
        assertThrows(IllegalStateException.class, () -> coordinator.tick(13));
        assertThrows(IllegalStateException.class, () -> hit(coordinator, shot(100, 5, false), 13));
    }

    @Test void closeClearsPendingQueueAndCrossThreadMutationFails() throws InterruptedException {
        var coordinator = coordinator(20, 20);
        hit(coordinator, shot(500, 5, false), 10);
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> { try { coordinator.close(); } catch (Throwable thrown) { failure.set(thrown); } });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(5, coordinator.metrics().queued());
        coordinator.close();
        assertEquals(5, coordinator.metrics().clearedChildren());
        assertEquals(0, coordinator.metrics().queued());
    }

    @Test void timingOverflowAndOwnerMismatchFailBeforeDamage() {
        var coordinator = coordinator(20, 20);
        var shot = shot(100, 5, false);
        assertThrows(ArithmeticException.class, () -> hit(coordinator, shot, Long.MAX_VALUE));
        assertTrue(encounter.impacts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> coordinator.physical(shot, impact(shot, 10), DamageModifiers.none(),
                new ProcCoordinator.Session(UUID.randomUUID(), UUID.randomUUID()), Optional.empty()));
        assertTrue(encounter.impacts().isEmpty());
    }

    @Test void invalidInjectedRandomCannotPartiallyCommitPhysicalDamage() {
        var coordinator = new ProcCoordinator(encounter, new ProcCoordinator.Limits(20, 20, 1, 2), () -> Double.NaN);
        coordinator.activate(session);
        assertThrows(IllegalArgumentException.class, () -> hit(coordinator, shot(25, 5, false), 10));
        assertTrue(encounter.impacts().isEmpty());
        assertEquals(0, coordinator.metrics().queued());
        assertEquals(0, coordinator.tempoBonus(session, 10));
    }

    @Test void partialDrainRetainsEarlierChildAndReportsConsumedOverflowWithoutRetry() {
        var scoreOnly = new CombatProfile(profile.mechanic(), CombatProfile.Mitigation.NONE, CombatProfile.Cap.NONE, 0);
        encounter = new CombatEncounter(new TargetState(encounterId, target, Double.MAX_VALUE, Double.MAX_VALUE, 0), "test", scoreOnly);
        var coordinator = coordinator(20, 20);
        var shot = shot(200, 0, false);
        var parent = coordinator.physical(shot, impact(shot, 10), new DamageModifiers(Map.of(), Map.of("large", 4e305)), session, Optional.empty());
        assertEquals(6e307, parent.damage().amounts().contributionDamage(), 1e292);
        var drain = coordinator.tickOutcomes(20);
        assertEquals(1, drain.results().size()); assertEquals(1, drain.failures().size());
        assertEquals(0, drain.results().getFirst().amounts().actualHealthDamage());
        assertEquals(1.2e308, encounter.contributions().get(owner).contributionDamage(), 1e293);
        assertEquals(2, encounter.acceptedOrdinal()); assertEquals(2, encounter.impacts().size());
        assertEquals(0, coordinator.metrics().queued());
        assertTrue(coordinator.tickOutcomes(20).results().isEmpty());
        assertTrue(coordinator.tick(21).isEmpty());
    }

    private ProcCoordinator coordinator(int capacity, int budget) {
        var coordinator = new ProcCoordinator(encounter, new ProcCoordinator.Limits(capacity, budget, 1, 2), () -> 0);
        assertTrue(coordinator.activate(session));
        return coordinator;
    }
    private ShotContext shot(double ferocity, int tempo, boolean duplex) {
        var stats = new StatResolver(StatProfile.calibration()).resolve("v1", Map.of(StatKey.WEAPON_DAMAGE, 100.0,
                StatKey.FEROCITY, ferocity), ModifierSources.empty()).snapshot();
        List<WeaponDefinition.Enchantment> enchants = tempo > 0
                ? List.of(new WeaponDefinition.Enchantment("fatal_tempo", tempo, WeaponDefinition.EnchantmentKind.ULTIMATE))
                : duplex ? List.of(new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE)) : List.of();
        return new ShotContext(encounterId, UUID.randomUUID(), UUID.randomUUID(), 0, duplex ? Optional.of(UUID.randomUUID()) : Optional.empty(),
                owner, new WeaponIdentity(UUID.randomUUID(), "test", 1, "v1"), stats, enchants, profile.mechanic(), 0,
                new Vector3(0, 0, 0), new Vector3(1, 0, 0), CritOutcome.CRITICAL, 1, duplex ? .2 : 1);
    }
    private PhysicalImpact impact(ShotContext shot, long tick) {
        return new PhysicalImpact(new PhysicalImpact.Key(encounterId, shot.projectileId(), target, 0), owner, tick, new Vector3(10, 0, 0), Optional.empty());
    }
    private ProcCoordinator.PhysicalResult hit(ProcCoordinator coordinator, ShotContext shot, long tick) {
        return coordinator.physical(shot, impact(shot, tick), DamageModifiers.none(), session, Optional.empty());
    }
}
