package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TracerAimedTest {
    private static final Vector3 ZERO = new Vector3(0, 0, 0), FORWARD = new Vector3(1, 0, 0);
    private static final TracerProfile PROFILE = TracerProfile.AIMED_V3;
    private static final UUID TARGET = new UUID(0, 1);
    private static TracerRules.Part point(Vector3 point) {
        return new TracerRules.Part(TARGET, new UUID(0, 2), new TracerRules.Box(point, point));
    }
    private Optional<TracerRules.Aim> aim(Vector3 position, int level, Vector3 point, boolean locked, Vector3 direction) {
        return TracerRules.acquire(position, level, List.of(point(point)), a -> true, PROFILE,
                locked ? Optional.of(TARGET) : Optional.empty(), ZERO, direction);
    }
    @Test void everyInclusiveRadiusAndRetentionBoundaryUsesCurrentPosition() {
        for (int level = 1; level <= 5; level++) {
            double radius = level * 4;
            for (boolean locked : List.of(false, true)) {
                double limit = radius + (locked ? 4 : 0);
                assertTrue(aim(ZERO, level, new Vector3(limit, 0, 0), locked, FORWARD).isPresent());
                assertTrue(aim(ZERO, level, new Vector3(Math.nextUp(limit), 0, 0), locked, FORWARD).isEmpty());
            }
        }
        assertTrue(aim(ZERO, 0, FORWARD, true, FORWARD).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> aim(ZERO, 6, FORWARD, false, FORWARD));
        // A far shot may naturally enter range later; launch distance is not an admission restriction.
        Vector3 target = new Vector3(100, 0, 0);
        assertTrue(aim(ZERO, 5, target, false, FORWARD).isEmpty());
        assertTrue(aim(new Vector3(80, 0, 0), 5, target, false, FORWARD).isPresent());
    }
    @Test void coneIsInclusiveInThreeDimensionsForAcquisitionAndRetention() {
        double angle = Math.toRadians(30);
        for (boolean locked : List.of(false, true)) {
            for (double delta : new double[]{-1e-10, 0, 1e-10}) {
                double a = angle + delta;
                assertEquals(delta <= 0, aim(ZERO, 5, new Vector3(Math.cos(a), Math.sin(a), 0), locked, FORWARD).isPresent());
                assertEquals(delta <= 0, aim(ZERO, 5, new Vector3(Math.cos(a), 0, Math.sin(a)), locked, FORWARD).isPresent());
            }
            assertTrue(aim(ZERO, 5, new Vector3(0, 1, 0), locked, FORWARD).isEmpty());
            assertTrue(aim(ZERO, 5, new Vector3(-1, 0, 0), locked, FORWARD).isEmpty());
        }
    }
    @Test void nearbyLoopCannotReplaceLaunchOriginOrDirection() {
        Vector3 nearby = new Vector3(-10, 0, 0), target = new Vector3(-9, 0, 0);
        assertTrue(aim(nearby, 5, target, false, FORWARD).isEmpty());
        assertTrue(aim(nearby, 5, target, true, FORWARD).isEmpty());
        assertTrue(aim(nearby, 5, target, false, new Vector3(-1, 0, 0)).isPresent());
        assertThrows(IllegalArgumentException.class, () -> TracerRules.acquire(ZERO, 5,
                List.of(point(FORWARD)), a -> true, PROFILE, Optional.empty()));
    }
    @Test void arbitraryAxisBoundarySurvivesNormalizationRoundoff() {
        double length = Math.sqrt(35), perpendicularLength = Math.sqrt(10);
        Vector3 direction = new Vector3(1 / length, 3 / length, 5 / length);
        for (double delta : new double[]{-1e-10, 0, 1e-10}) {
            double angle = Math.toRadians(30) + delta;
            Vector3 point = new Vector3(Math.cos(angle) / length - Math.sin(angle) * 3 / perpendicularLength,
                    Math.cos(angle) * 3 / length + Math.sin(angle) / perpendicularLength, Math.cos(angle) * 5 / length);
            assertEquals(delta <= 0, TracerRules.withinLaunchCone(direction, point, PROFILE.aimHalfAngleRadians()));
        }
    }
    @Test void zeroVectorsCannotAuthorizeAndFiniteExtremesAreExplicit() {
        assertTrue(aim(ZERO, 5, FORWARD, false, ZERO).isEmpty());
        assertTrue(aim(ZERO, 5, ZERO, true, FORWARD).isEmpty());
        assertTrue(TracerRules.withinLaunchCone(new Vector3(Double.MIN_VALUE, 0, 0), FORWARD, PROFILE.aimHalfAngleRadians()));
        assertTrue(TracerRules.withinLaunchCone(new Vector3(1e300, 0, 0), new Vector3(1e300, 0, 0), PROFILE.aimHalfAngleRadians()));
        assertThrows(IllegalArgumentException.class, () -> TracerRules.withinLaunchCone(
                new Vector3(Double.MAX_VALUE, Double.MAX_VALUE, 0), FORWARD, PROFILE.aimHalfAngleRadians()));
        assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TracerRules.withinLaunchCone(FORWARD, FORWARD, Double.NaN));
    }
    @Test void obstructionGenerationAndOffConePartsReleaseLockWithoutHidingEligibleParts() {
        var inside = point(new Vector3(10, 0, 0)); var outside = point(new Vector3(0, 1, 0));
        assertEquals(inside, TracerRules.acquire(ZERO, 5, List.of(outside, inside), a -> true,
                PROFILE, Optional.of(TARGET), ZERO, FORWARD).orElseThrow().part());
        assertTrue(TracerRules.acquire(ZERO, 5, List.of(inside), a -> false,
                PROFILE, Optional.of(TARGET), ZERO, FORWARD).isEmpty());
        assertTrue(TracerRules.acquire(ZERO, 5, List.of(), a -> true,
                PROFILE, Optional.of(TARGET), ZERO, FORWARD).isEmpty());
        assertTrue(TracerRules.acquire(ZERO, 5, List.of(point(new Vector3(24, 0, 0))), a -> true,
                PROFILE, Optional.of(UUID.randomUUID()), ZERO, FORWARD).isEmpty());
    }
    @Test void surfaceAimGraceSpeedAndLegacyBoundsRemainDistinct() {
        var part = new TracerRules.Part(TARGET, new UUID(0, 2),
                new TracerRules.Box(new Vector3(10, 0, 0), new Vector3(11, 100, 100)));
        assertEquals(new Vector3(10, 0, 0), TracerRules.acquire(ZERO, 5, List.of(part), a -> true,
                PROFILE, Optional.empty(), ZERO, FORWARD).orElseThrow().point());
        for (long tick = 40; tick < 43; tick++) assertTrue(PROFILE.ballistic(tick, 40));
        assertFalse(PROFILE.ballistic(43, 40));
        var turned = TracerRules.steer(new Vector3(3, 0, 0), new Vector3(1, 1, 0), PROFILE.turnRadians());
        assertEquals(3, TracerRules.length(turned), 1e-12);
        assertEquals(Math.toRadians(6), Math.acos(turned.x() / 3), 1e-12);
        assertTrue(PROFILE.requiresWholePartBounds());
        assertTrue(TracerProfile.RETURN_V2.requiresWholePartBounds());
        assertFalse(TracerProfile.CALIBRATION_V1.requiresWholePartBounds());
    }
}
