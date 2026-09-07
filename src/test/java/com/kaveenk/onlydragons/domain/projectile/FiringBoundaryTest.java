package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.stats.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FiringBoundaryTest {
    private ShotContext shot(UUID group, UUID owner) {
        var item = CalibrationLoadouts.registry().resolve(CalibrationLoadouts.registry().create("duplex"));
        var stats = new StatSnapshotFactory(StatProfile.calibration()).create("captured", item.resolvedWeapon(), ModifierSources.empty()).snapshot();
        return new ShotContext(UUID.randomUUID(), group, UUID.randomUUID(), 0, Optional.empty(), owner,
                item.instance().identity(), stats, item.enchantments(), new MechanicRevision("firing-calibration", "v1"),
                20, new Vector3(2, 3, 4), new Vector3(0, 0, 3), CritOutcome.CRITICAL, 0.5, 1);
    }
    @Test void wholeGroupsReserveAndRollbackWithoutOversubscription() {
        ArrowRegistry registry = new ArrowRegistry(3); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(registry.reserve(a, 2)); assertFalse(registry.reserve(a, 1));
        assertFalse(registry.reserve(b, 2)); assertEquals(2, registry.used());
        assertTrue(registry.reserve(b, 1)); registry.release(a); registry.release(a);
        assertEquals(1, registry.used()); assertTrue(registry.reserve(UUID.randomUUID(), 2));
    }
    @Test void twoOwnersKeepDistinctClaimsAtSameTickAndRetainRejectedTerminalClaim() {
        ArrowRegistry registry = new ArrowRegistry(4); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var one = new OwnedProjectile(shot(a, UUID.randomUUID()), UUID.randomUUID());
        var two = new OwnedProjectile(shot(b, UUID.randomUUID()), UUID.randomUUID());
        registry.reserve(a, 2); registry.reserve(b, 2); registry.emit(one); registry.emit(two);
        assertEquals(4, registry.used()); assertEquals(2, registry.reserved());
        assertEquals(one, registry.claim(one.shot().projectileId()).orElseThrow());
        assertTrue(registry.claim(one.shot().projectileId()).isEmpty());
        assertEquals(two, registry.claim(two.shot().projectileId()).orElseThrow());
        registry.retire(one.shot().projectileId());
        assertTrue(registry.claim(one.shot().projectileId()).isEmpty());
        assertEquals(two, registry.lookup(two.shot().projectileId()).orElseThrow());
        registry.clear(); assertEquals(0, registry.used());
    }
    @Test void childIsDistinctCapturedAndCannotCreateGrandchild() {
        var primary = shot(UUID.randomUUID(), UUID.randomUUID());
        for (int level = 1; level <= 5; level++) {
            var child = FiringRules.child(primary, UUID.randomUUID(), 21, level);
            assertEquals(primary.projectileId(), child.parentProjectileId().orElseThrow());
            assertNotEquals(primary.projectileId(), child.projectileId()); assertEquals(primary.shotId(), child.shotId());
            assertSame(primary.stats(), child.stats()); assertEquals(primary.weapon(), child.weapon());
            assertEquals(primary.enchantments(), child.enchantments()); assertEquals(primary.crit(), child.crit());
            assertEquals(primary.launchPosition(), child.launchPosition()); assertEquals(primary.initialVelocity(), child.initialVelocity());
            assertEquals(level * 0.04, child.projectileScale(), 1e-12); assertEquals(0.5, child.drawScale());
            assertThrows(IllegalArgumentException.class, () -> FiringRules.child(child, UUID.randomUUID(), 22, 5));
        }
        assertThrows(IllegalArgumentException.class, () -> FiringRules.child(primary, UUID.randomUUID(), 20, 5));
        assertThrows(IllegalArgumentException.class, () -> FiringRules.duplexScale(6));
    }
    @Test void duplexKeepsOriginalTracerGraceAndProfile() {
        var shot = shot(UUID.randomUUID(), UUID.randomUUID());
        for (var profile : com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile.values()) {
            var primary = new OwnedProjectile(shot, UUID.randomUUID(), profile, 20);
            var child = primary.child(FiringRules.child(shot, UUID.randomUUID(), 21, 5));
            assertSame(profile, child.tracerProfile());
            assertEquals(shot.launchPosition(), child.shot().launchPosition());
            assertEquals(shot.initialVelocity(), child.shot().initialVelocity());
            assertEquals(20, child.groupLaunchTick());
            assertEquals(21, child.shot().launchTick());
            assertEquals(profile != com.kaveenk.onlydragons.domain.projectile.homing.TracerProfile.CALIBRATION_V1,
                    child.tracerProfile().ballistic(22, child.groupLaunchTick()));
            assertFalse(child.tracerProfile().ballistic(23, child.groupLaunchTick()));
            assertThrows(IllegalArgumentException.class, () -> primary.child(shot(UUID.randomUUID(), UUID.randomUUID())));
        }
    }
    @Test void cadenceBoundariesRetainHighAttackSpeed() {
        assertEquals(10, FiringRules.cooldown(0)); assertEquals(6, FiringRules.cooldown(99.99));
        assertEquals(5, FiringRules.cooldown(100)); assertEquals(5, FiringRules.cooldown(100.01));
        assertEquals(1, FiringRules.cooldown(1e6));
        assertThrows(IllegalArgumentException.class, () -> FiringRules.cooldown(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> FiringRules.cooldown(-1));
    }
    @Test void settlementNeverBackdatesReceiverClockAndPreservesVeto() {
        var owned = new OwnedProjectile(shot(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID());
        var impact = new PhysicalImpact(new PhysicalImpact.Key(owned.shot().encounterId(), owned.shot().projectileId(), UUID.randomUUID(), 0),
                owned.shot().ownerId(), 31, new Vector3(0, 0, 0), Optional.of("physical-part-uuid"));
        var hit = new SettledHit(owned, impact, 30, 31, Optional.of(SettledHit.Rejection.PHYSICAL_VETO));
        assertFalse(hit.accepted()); assertEquals(31, hit.impact().tick()); assertEquals(30, hit.collisionTick());
        assertThrows(IllegalArgumentException.class, () -> new SettledHit(owned, impact, 30, 30, Optional.empty()));
    }
    @Test void sessionIdentityIsIndependentOfEquipmentAndSnapshotsAreReadOnly() throws Exception {
        var shot = shot(UUID.randomUUID(), UUID.randomUUID());
        var old = new OwnedProjectile(shot, UUID.randomUUID()); var next = new OwnedProjectile(shot, UUID.randomUUID());
        assertNotEquals(old.sessionToken(), next.sessionToken()); assertSame(old.shot().stats(), next.shot().stats());
        ArrowRegistry registry = new ArrowRegistry(1); registry.reserve(shot.shotId(), 1); registry.emit(old);
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> { try { registry.clear(); } catch (Throwable ex) { failure.set(ex); } });
        thread.start(); thread.join(); assertInstanceOf(IllegalStateException.class, failure.get()); assertEquals(1, registry.used());
    }
}
