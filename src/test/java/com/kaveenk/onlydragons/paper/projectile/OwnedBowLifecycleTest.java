package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.UUID;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class OwnedBowLifecycleTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    PlayerMock player;
    OwnedBowService bows;
    UUID encounter;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        player = server.addPlayer(); bows = plugin.bows(); encounter = UUID.randomUUID();
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    void admit() { bows.openEncounter(encounter, player.getWorld(), new BoundingBox(-1000, -1000, -1000, 1000, 1000, 1000), new MechanicRevision("calibration", "v1")); }
    @Test void admissionActivatesOnlinePlayersBeforeAnyTargetAndResetInvalidatesSessions() {
        assertTrue(bows.currentSession(player.getUniqueId()).isEmpty()); admit();
        UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        assertTrue(bows.isCurrentSession(player.getUniqueId(), token)); assertTrue(bows.projectiles().isEmpty());
        bows.endEncounter(encounter); assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
        encounter = UUID.randomUUID(); admit(); assertNotEquals(token, bows.currentSession(player.getUniqueId()).orElseThrow());
    }
    @Test void staleSessionClearCannotInvalidateReplacement() {
        admit(); UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(), token, false); bows.activate(player);
        UUID replacement = bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(), token, true);
        assertEquals(replacement, bows.currentSession(player.getUniqueId()).orElseThrow());
        assertFalse(bows.isCurrentSession(player.getUniqueId(), token));
    }
    @Test void overlappingAdmissionAndCompetingReceiversRejectWithoutReplacingAuthority() {
        admit();
        assertThrows(IllegalArgumentException.class, () -> bows.openEncounter(UUID.randomUUID(), player.getWorld(),
                new BoundingBox(-1, -1, -1, 1, 1, 1), new MechanicRevision("other", "v1")));
        java.util.function.Consumer<com.kaveenk.onlydragons.domain.projectile.SettledHit> receiver = hit -> {};
        bows.receiver(receiver); assertThrows(IllegalStateException.class, () -> bows.receiver(hit -> {}));
        bows.clearReceiver(hit -> {}); assertThrows(IllegalStateException.class, () -> bows.receiver(hit -> {}));
        bows.clearReceiver(receiver); assertDoesNotThrow(() -> bows.receiver(hit -> {}));
    }
    @Test void directArenaTransferReplacesSessionWithoutWaitingForAnUnadmittedLocation() {
        bows.openEncounter(encounter, player.getWorld(), new BoundingBox(-10, -1000, -10, 10, 1000, 10), new MechanicRevision("calibration", "v1"));
        UUID old = bows.currentSession(player.getUniqueId()).orElseThrow();
        UUID second = UUID.randomUUID();
        bows.openEncounter(second, player.getWorld(), new BoundingBox(200, -1000, -10, 300, 1000, 10), new MechanicRevision("calibration", "v1"));
        player.teleport(new org.bukkit.Location(player.getWorld(), 250, 64, 0));
        bows.activate(player);
        UUID next = bows.currentSession(player.getUniqueId()).orElseThrow();
        assertNotEquals(old, next); bows.endEncounter(encounter);
        assertEquals(next, bows.currentSession(player.getUniqueId()).orElseThrow());
        bows.endEncounter(second); assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
    }
    @Test void disableIsTerminalIdempotentAndCancelsOnlyOwnedTask() {
        admit(); assertEquals(1, bows.taskCount()); bows.close(); bows.close();
        assertEquals(0, bows.taskCount()); assertEquals(0, bows.capacityUsed());
        assertEquals(0, bows.pendingClaims()); assertEquals(0, bows.pendingGroups());
        assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
        assertThrows(IllegalStateException.class, () -> bows.activate(player));
        assertTrue(plugin.equipment().loadouts().contains("ordinary"));
    }
}
