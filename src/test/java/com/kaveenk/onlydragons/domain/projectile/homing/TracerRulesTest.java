package com.kaveenk.onlydragons.domain.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TracerRulesTest {
    private static final Vector3 ZERO = new Vector3(0, 0, 0);
    private static TracerRules.Part part(long target, long id, double x) {
        return new TracerRules.Part(new UUID(0, target), new UUID(0, id),
                new TracerRules.Box(new Vector3(x, -1, -1), new Vector3(x + 1, 1, 1)));
    }
    @Test void everyLevelIncludesExactSurfaceRadiusButExcludesNextRepresentableDistance() {
        for (int level = 1; level <= 5; level++) {
            double radius = level * 2;
            for (double x : new double[]{Math.nextDown(radius), radius}) {
                var aim = TracerRules.acquire(ZERO, level, List.of(part(1, 1, x)), a -> true).orElseThrow();
                assertEquals(x, aim.distance()); assertEquals(new Vector3(x, 0, 0), aim.point());
            }
            assertTrue(TracerRules.acquire(ZERO, level, List.of(part(1, 1, Math.nextUp(radius))), a -> true).isEmpty());
        }
        assertTrue(TracerRules.acquire(ZERO, 0, List.of(part(1, 1, 0)), a -> true).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TracerRules.radius(-1));
        assertThrows(IllegalArgumentException.class, () -> TracerRules.radius(6));
    }
    @Test void threeDimensionalSurfaceDistanceUsesBoxNotCenter() {
        var p = new TracerRules.Part(new UUID(0, 1), new UUID(0, 1),
                new TracerRules.Box(new Vector3(2, 3, 6), new Vector3(100, 100, 100)));
        assertEquals(7, TracerRules.acquire(ZERO, 4, List.of(p), a -> true).orElseThrow().distance());
        assertTrue(TracerRules.acquire(ZERO, 3, List.of(p), a -> true).isEmpty());
    }
    @Test void tiesAreIndependentOfInputOrderAndObstructionFallsBackToAnotherVisiblePart() {
        var first = part(1, 1, 2); var second = part(1, 2, 2); var third = part(2, 1, 2);
        for (var list : List.of(List.of(third, second, first), List.of(first, third, second))) {
            assertEquals(first, TracerRules.acquire(ZERO, 1, list, a -> true).orElseThrow().part());
            assertEquals(second, TracerRules.acquire(ZERO, 1, list, a -> !a.part().equals(first)).orElseThrow().part());
        }
        assertTrue(TracerRules.acquire(ZERO, 5, List.of(first), a -> false).isEmpty());
        assertTrue(TracerRules.acquire(ZERO, 5, List.of(), a -> true).isEmpty());
        assertEquals(first, TracerRules.acquire(ZERO, 1, List.of(first), a -> true).orElseThrow().part());
    }
    @Test void zeroSpeedCoincidentAimAndZeroTurnNeverInventMotion() {
        var speed = new Vector3(3, 0, 0);
        assertEquals(ZERO, TracerRules.steer(ZERO, speed, 0.1));
        assertEquals(speed, TracerRules.steer(speed, ZERO, 0.1));
        assertEquals(speed, TracerRules.steer(speed, new Vector3(0, 1, 0), 0));
    }
    @Test void orthogonalAndAntiparallelTurnsPreserveSpeedAndRespectSixDegreeLimit() {
        var speed = new Vector3(3, 0, 0);
        for (var target : List.of(new Vector3(0, 1, 0), new Vector3(-1, 0, 0), new Vector3(-1, 1e-15, 0))) {
            var after = TracerRules.steer(speed, target, TracerRules.TURN_RADIANS);
            assertEquals(3, TracerRules.length(after), 1e-12);
            assertEquals(Math.cos(Math.toRadians(6)), after.x() / 3, 1e-12);
            assertTrue(after.y() > 0);
            assertEquals(after, TracerRules.steer(speed, target, TracerRules.TURN_RADIANS));
        }
        var near = TracerRules.steer(speed, new Vector3(1, 0.01, 0), TracerRules.TURN_RADIANS);
        assertEquals(0.01, near.y()/near.x(), 1e-12);
    }
    @Test void arbitraryAxesAndTinyMagnitudesStayFiniteWithoutBoosting() {
        for (var velocity : List.of(new Vector3(1, 2, 3), new Vector3(0, -3, 0), new Vector3(0, 0, 3), new Vector3(1e-300, 0, 0))) {
            var opposite = new Vector3(-velocity.x(), -velocity.y(), -velocity.z());
            var after = TracerRules.steer(velocity, opposite, TracerRules.TURN_RADIANS);
            assertEquals(TracerRules.length(velocity), TracerRules.length(after), TracerRules.length(velocity)*1e-12);
        }
        assertDoesNotThrow(() -> TracerRules.steer(new Vector3(Double.MIN_VALUE, 0, 0), new Vector3(0, 1, 0), 0.1));
        assertThrows(IllegalArgumentException.class, () -> TracerRules.steer(ZERO, ZERO, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> TracerRules.steer(ZERO, ZERO, -1));
        assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TracerRules.Box(new Vector3(1, 1, 1), ZERO));
    }
}
