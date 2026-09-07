package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules.Box;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic broker tests with an injected native-ticket set and add failure. Exact set contents distinguish borrowed tickets from owned references; no claim is made that these mock operations sustain native entity ticking.
 */
class ArenaTicketsTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    ArenaTickets tickets;
    java.util.Set<String> nativeTickets = new java.util.HashSet<>();
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() { server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class); tickets = new ArenaTickets(new ArenaTickets.TicketAccess() {
        /** Models native add ownership using a set; false means an existing same-plugin ticket.
 * @param world mock world, unused by this single-world set
 * @param x chunk X
 * @param z chunk Z
 * @return whether the key was newly inserted
 */ public boolean add(org.bukkit.World world, int x, int z) { return nativeTickets.add(x + ":" + z); }
        /** Removes one owned simulated ticket; exact remaining set contents are the oracle.
 * @param world mock world
 * @param x chunk X
 * @param z chunk Z
 */ public void remove(org.bukkit.World world, int x, int z) { nativeTickets.remove(x + ":" + z); }
    }); }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * A one-chunk arena reserves nine padded slots without loading; a rejected giant footprint leaves the original charge unchanged.
     */
    @Test void reservationRejectsOversizedArenaWithoutLoadingOrChangingExistingBudget() {
        var world = server.addSimpleWorld("tickets"); var id = UUID.randomUUID();
        tickets.reserve(id, world, new Box(new Vector3(0, 0, 0), new Vector3(16, 100, 16)));
        assertEquals(9, tickets.reservedCount()); assertEquals(0, tickets.ticketCount());
        assertThrows(IllegalArgumentException.class, () -> tickets.reserve(UUID.randomUUID(), world,
                new Box(new Vector3(-30000000, 0, -30000000), new Vector3(30000000, 100, 30000000))));
        assertEquals(9, tickets.reservedCount());
        tickets.endArena(id); assertEquals(0, tickets.reservedCount());
    }
    /**
     * Two demand IDs share nine keys; movement/release preserve the remaining demand and the preexisting same-plugin ticket.
     */
    @Test void twoConsumersShareTicketsAndOneReleasePreservesTheOthersDemand() {
        var world = server.addSimpleWorld("tickets"); var arena = UUID.randomUUID();
        tickets.reserve(arena, world, new Box(new Vector3(0, 0, 0), new Vector3(32, 100, 32)));
        nativeTickets.add("0:0"); // Pre-existing same-plugin ticket belongs to another owner.
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        tickets.retain(a, arena, 0, 0); tickets.retain(b, arena, 0, 0);
        assertEquals(9, tickets.ticketCount()); tickets.release(a);
        assertEquals(9, tickets.ticketCount()); assertEquals(1, tickets.demandCount());
        tickets.retain(b, arena, 1, 0); assertEquals(9, tickets.ticketCount());
        tickets.endArena(arena); assertEquals(0, tickets.ticketCount()); assertEquals(0, tickets.demandCount());
        assertEquals(java.util.Set.of("0:0"), nativeTickets);
        tickets.close(); assertThrows(IllegalStateException.class, () -> tickets.retain(a, arena, 0, 0));
    }
    /**
     * An injected add failure rolls back acquired keys and demands while retaining the original reservation until close.
     */
    @Test void failedNativeAcquisitionRollsBackOnlyNewReferences() {
        var world = server.addSimpleWorld("failure"); var arena = UUID.randomUUID();
        var held = new java.util.HashSet<String>();
        var broker = new ArenaTickets(new ArenaTickets.TicketAccess() {
            /** Fails on the centre chunk after earlier acquisitions to exercise rollback.
 * @param w mock world
 * @param x chunk X
 * @param z chunk Z
 * @return whether a nonfailing key was new
 * @throws IllegalStateException for centre (0,0)
 */ public boolean add(org.bukkit.World w, int x, int z) {
                if (x == 0 && z == 0) throw new IllegalStateException("fixture add failure");
                return held.add(x + ":" + z);
            }
            /** Removes a newly acquired simulated reference during rollback.
 * @param w mock world
 * @param x chunk X
 * @param z chunk Z
 */ public void remove(org.bukkit.World w, int x, int z) { held.remove(x + ":" + z); }
        });
        broker.reserve(arena, world, new Box(new Vector3(0, 0, 0), new Vector3(16, 100, 16)));
        assertThrows(IllegalStateException.class, () -> broker.retain(UUID.randomUUID(), arena, 0, 0));
        assertTrue(held.isEmpty()); assertEquals(0, broker.ticketCount()); assertEquals(0, broker.demandCount());
        assertEquals(9, broker.reservedCount()); broker.close(); assertEquals(0, broker.reservedCount());
    }

}
