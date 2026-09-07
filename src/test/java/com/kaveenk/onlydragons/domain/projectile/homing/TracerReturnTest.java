package com.kaveenk.onlydragons.domain.projectile.homing;

import static org.junit.jupiter.api.Assertions.*;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.*;
import org.junit.jupiter.api.Test;

class TracerReturnTest {
    private static final Vector3 ZERO = new Vector3(0, 0, 0);
    private static TracerRules.Part part(UUID target, double x) {
        return new TracerRules.Part(target, UUID.randomUUID(), new TracerRules.Box(new Vector3(x, -1, -1), new Vector3(x+1, 1, 1)));
    }
    @Test void everyLevelHasInclusiveAcquisitionAndSeparateRetentionBoundary() {
        UUID target = UUID.randomUUID();
        for (int level = 1; level <= 5; level++) {
            double radius = 8 * level;
            assertTrue(aim(level, part(target, radius), Optional.empty()).isPresent());
            assertTrue(aim(level, part(target, Math.nextUp(radius)), Optional.empty()).isEmpty());
            assertTrue(aim(level, part(target, radius+8), Optional.of(target)).isPresent());
            assertTrue(aim(level, part(target, Math.nextUp(radius+8)), Optional.of(target)).isEmpty());
            assertTrue(aim(level, part(target, radius+8), Optional.of(UUID.randomUUID())).isEmpty());
            assertEquals(level * 2, TracerProfile.CALIBRATION_V1.radius(level));
        }
        assertTrue(aim(0, part(target, 0), Optional.of(target)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TracerProfile.RETURN_V2.radius(6));
    }
    private Optional<TracerRules.Aim> aim(int level, TracerRules.Part part, Optional<UUID> lock) {
        return TracerRules.acquire(ZERO, level, List.of(part), a -> true, TracerProfile.RETURN_V2, lock);
    }
    @Test void lockUsesClosestVisibleCurrentPartAndObstructionCannotExtendRange() {
        UUID target = UUID.randomUUID();
        var blocked = part(target, 9); var visible = part(target, 12);
        var aim = TracerRules.acquire(ZERO, 1, List.of(blocked, visible), a -> a.part() != blocked,
                TracerProfile.RETURN_V2, Optional.of(target));
        assertEquals(visible, aim.orElseThrow().part());
        assertTrue(TracerRules.acquire(ZERO, 1, List.of(blocked, visible), a -> false,
                TracerProfile.RETURN_V2, Optional.of(target)).isEmpty());
        assertTrue(TracerRules.acquire(ZERO, 1, List.of(), a -> true,
                TracerProfile.RETURN_V2, Optional.of(target)).isEmpty());
        assertTrue(TracerRules.acquire(ZERO, 1, List.of(visible), a -> true,
                TracerProfile.RETURN_V2, Optional.empty()).isEmpty());
    }
    @Test void graceIsExactlyThreeOriginalGroupTicksAndTurnPreservesSpeed() {
        for (long tick=40; tick<43; tick++) assertTrue(TracerProfile.RETURN_V2.ballistic(tick,40));
        assertFalse(TracerProfile.RETURN_V2.ballistic(43,40));
        assertFalse(TracerProfile.CALIBRATION_V1.ballistic(40,40));
        assertThrows(IllegalArgumentException.class, () -> TracerProfile.RETURN_V2.ballistic(39,40));
        Vector3 turned = TracerRules.steer(new Vector3(0,3,0),new Vector3(0,-1,0),TracerProfile.RETURN_V2.turnRadians());
        assertEquals(3,TracerRules.length(turned),1e-12);
        assertEquals(Math.toRadians(18),Math.acos(turned.y()/3),1e-12);
    }
    @Test void onlyNamedTrustedDefinitionSelectsV2AndOldLoadoutsKeepV1() {
        var registry = CalibrationLoadouts.registry();
        for (String id : List.of("ordinary","crit","ferocity_25","ferocity_100","ferocity_500","tracer","duplex","fatal_tempo","shortbow_v1"))
            assertEquals(TracerProfile.CALIBRATION_V1,TracerProfile.forDefinition(registry.resolve(registry.create(id)).definition().weapon()));
        assertEquals(TracerProfile.RETURN_V2,TracerProfile.forDefinition(registry.resolve(registry.create("tracer_return_v2")).definition().weapon()));
    }
}
