package com.kaveenk.onlydragons.domain.encounter.motion;

import static org.junit.jupiter.api.Assertions.*;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules;
import org.junit.jupiter.api.Test;

/**
 * Pure route-envelope regression over 4,000 ticks at arena radii 16/24/48. Verifies smoothstep
 * entry, per-step displacement/yaw and the declared 12-block margin, plus invalid inputs.
 * This mathematical envelope does not establish actual multipart bounds or rendered smoothness.
 */
class DragonOrbitTest {
    @Test void smallestNormalAndLargestArenasFitSweptEnvelopeAndSmoothEntry() {
        for (double arena : new double[]{16,24,48}) {
            var route = DragonOrbit.forArena(arena);
            var previous = route.at(0);
            assertEquals(0, TracerRules.length(previous.offset()));
            for (int tick=1;tick<=4000;tick++) {
                var pose = route.at(tick);
                assertTrue(TracerRules.length(TracerRules.subtract(pose.offset(),previous.offset()))<=.25);
                double yaw = Math.abs(((pose.yaw()-previous.yaw()+540)%360)-180);
                assertTrue(yaw<=3.00001);
                assertTrue(Math.abs(pose.offset().x())+12<=arena);
                assertTrue(Math.abs(pose.offset().z())+12<=arena);
                assertTrue(Math.abs(pose.offset().y())<=1);
                previous=pose;
            }
        }
        assertEquals(4,DragonOrbit.forArena(16).radius());
        assertEquals(8,DragonOrbit.forArena(24).radius());
        assertEquals(8,DragonOrbit.forArena(48).radius());
    }
    @Test void unsafeInputsRejectWithoutProducingRoute() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,15.999,48.001})
            assertThrows(IllegalArgumentException.class,()->DragonOrbit.forArena(value));
        assertThrows(IllegalArgumentException.class,()->new DragonOrbit(3));
        assertThrows(IllegalArgumentException.class,()->new DragonOrbit(9));
        assertThrows(IllegalArgumentException.class,()->new DragonOrbit(4).at(-1));
    }
}
