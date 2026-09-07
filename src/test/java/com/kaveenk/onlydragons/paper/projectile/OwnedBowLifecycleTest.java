package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import java.util.UUID;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MockBukkit admission/generation and single-receiver ownership tests. Tokens, task counts and registry state are inspected directly; no actual bow input, collision or client disconnect timing is claimed.
 */
class OwnedBowLifecycleTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    PlayerMock player;
    OwnedBowService bows;
    UUID encounter;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        player = server.addPlayer(); bows = plugin.bows(); encounter = UUID.randomUUID();
    }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * Opens a broad targetless calibration arena containing the mock player; the service owns reservation/session creation.
     */
    void admit() { bows.openEncounter(encounter, player.getWorld(), new BoundingBox(-1000, -1000, -1000, 1000, 1000, 1000), new MechanicRevision("calibration", "v1")); }
    /**
     * A targetless arena admits already-online players, and reset followed by a new generation produces a different token.
     */
    @Test void admissionActivatesOnlinePlayersBeforeAnyTargetAndResetInvalidatesSessions() {
        assertTrue(bows.currentSession(player.getUniqueId()).isEmpty()); admit();
        UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        assertTrue(bows.isCurrentSession(player.getUniqueId(), token)); assertTrue(bows.projectiles().isEmpty());
        bows.endEncounter(encounter); assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
        encounter = UUID.randomUUID(); admit(); assertNotEquals(token, bows.currentSession(player.getUniqueId()).orElseThrow());
    }
    /**
     * Cleanup carrying an old token cannot delete the newly activated session.
     */
    @Test void staleSessionClearCannotInvalidateReplacement() {
        admit(); UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(), token, false); bows.activate(player);
        UUID replacement = bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(), token, true);
        assertEquals(replacement, bows.currentSession(player.getUniqueId()).orElseThrow());
        assertFalse(bows.isCurrentSession(player.getUniqueId(), token));
    }
    /**
     * Overlapping arenas fail before adoption; callback detachment uses exact identity so an unrelated consumer cannot seize authority.
     */
    @Test void overlappingAdmissionAndCompetingReceiversRejectWithoutReplacingAuthority() {
        admit();
        assertThrows(IllegalArgumentException.class, () -> bows.openEncounter(UUID.randomUUID(), player.getWorld(),
                new BoundingBox(-1, -1, -1, 1, 1, 1), new MechanicRevision("other", "v1")));
        java.util.function.Consumer<com.kaveenk.onlydragons.domain.projectile.SettledHit> receiver = hit -> {};
        var isolated = new OwnedBowService(plugin, plugin.equipment(), 10, () -> 0);
        isolated.receiver(receiver); assertThrows(IllegalStateException.class, () -> isolated.receiver(hit -> {}));
        isolated.clearReceiver(hit -> {}); assertThrows(IllegalStateException.class, () -> isolated.receiver(hit -> {}));
        isolated.clearReceiver(receiver); assertDoesNotThrow(() -> isolated.receiver(hit -> {}));
    }
    /**
     * Direct movement between nonoverlapping admissions replaces the token; closing the old arena cannot clear the new session.
     */
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
    /**
     * Repeated close clears task/capacity/claims/session state and rejects activation while leaving the equipment collaborator available.
     */
    @Test void disableIsTerminalIdempotentAndCancelsOnlyOwnedTask() {
        admit(); assertEquals(1, bows.taskCount()); bows.close(); bows.close();
        assertEquals(0, bows.taskCount()); assertEquals(0, bows.capacityUsed());
        assertEquals(0, bows.pendingClaims()); assertEquals(0, bows.pendingGroups());
        assertTrue(bows.currentSession(player.getUniqueId()).isEmpty());
        assertThrows(IllegalStateException.class, () -> bows.activate(player));
        assertTrue(plugin.equipment().loadouts().contains("ordinary"));
    }
}
