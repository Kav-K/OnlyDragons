package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.UUID;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Admission-only MockBukkit regression: oversized footprint rejection is atomic and returned value boxes cannot mutate live bounds. Counts and exact half-open faces are checked; this does not prove native steering, chunk ticking or real arrows.
 */
class HomingAdmissionTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() { server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class); }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * Compares empty admission/session/budget after failure, exact box faces and immutable retained snapshots, then verifies close rejects future continuity ticks.
     */
    @Test void oversizedAdmissionIsAtomicAndViewsCannotRewriteLiveBounds() {
        var player = server.addPlayer(); var bows = plugin.bows(); var id = UUID.randomUUID();
        var mechanic = new MechanicRevision("fixture", "v1");
        assertThrows(IllegalArgumentException.class, () -> bows.openEncounter(id, player.getWorld(),
                new BoundingBox(-30000000, 0, -30000000, 30000000, 200, 30000000), mechanic));
        assertTrue(bows.admittedArenas().isEmpty()); assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
        assertEquals(0, bows.continuity().tickets().reservedCount());
        var bounds = new BoundingBox(-10, -100, -10, 10, 200, 10);
        bows.openEncounter(id, player.getWorld(), bounds, mechanic);
        var snapshot = bows.admittedArenas(); bounds.shift(10000, 0, 0);
        assertEquals(-10, snapshot.getFirst().bounds().min().x());
        var box = snapshot.getFirst().bounds();
        assertTrue(box.contains(new com.kaveenk.onlydragons.domain.projectile.Vector3(-10, -100, -10)));
        assertTrue(box.contains(new com.kaveenk.onlydragons.domain.projectile.Vector3(Math.nextDown(10.0), Math.nextDown(200.0), Math.nextDown(10.0))));
        for (var face : java.util.List.of(new com.kaveenk.onlydragons.domain.projectile.Vector3(10, 0, 0),
                new com.kaveenk.onlydragons.domain.projectile.Vector3(0, 200, 0), new com.kaveenk.onlydragons.domain.projectile.Vector3(0, 0, 10))) assertFalse(box.contains(face));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        bows.activate(player); assertTrue(bows.currentSession(player.getUniqueId()).isPresent());
        bows.endEncounter(id); assertEquals(1, snapshot.size()); assertTrue(bows.admittedArenas().isEmpty());
        assertEquals(0, bows.continuity().tickets().reservedCount());
        var retained = bows.continuity(); bows.close();
        assertThrows(IllegalStateException.class, () -> retained.tick(null, null, java.util.List.of(), 0));
        assertDoesNotThrow(retained::close);
    }
}
