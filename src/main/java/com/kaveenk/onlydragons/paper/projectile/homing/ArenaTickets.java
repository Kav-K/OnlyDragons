package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules.Box;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Server-thread broker for all same-plugin chunk demands. Distinct demand UUIDs share
 * reference counts; callers never remove raw plugin tickets behind this broker. A native
 * ticket already present for this plugin is borrowed, not adopted for later removal.
 * Reservation bounds potential footprint without eagerly loading the entire arena.
 * @see ArrowContinuity
 */
public final class ArenaTickets implements AutoCloseable {
    // Reservation is potential footprint, not eagerly loaded chunks. Existing broad calibration arenas fit.
    /**
     * Maximum total potential reserved chunk footprint, including the one-chunk neighbourhood margin; not a count of eagerly loaded chunks.
     */
    public static final int MAX_RESERVED_CHUNKS = 32768;
    /**
     * Inclusive chunk-coordinate reservation with its precomputed capacity charge.
     * @param world native world
     * @param minX minimum reserved chunk X
     * @param maxX maximum reserved chunk X
     * @param minZ minimum reserved chunk Z
     * @param maxZ maximum reserved chunk Z
     * @param count charged footprint in chunks
     */
    private record Region(World world, int minX, int maxX, int minZ, int maxZ, int count) {}
    /**
     * Native ticket identity includes the world, not coordinates alone.
     * @param world native world
     * @param x chunk X
     * @param z chunk Z
     */
    private record Key(World world, int x, int z) {}
    /**
     * One consumer's retained neighbourhood.
     * @param arena generation owning the reservation
     * @param chunks immutable current 3-by-3 chunk set
     */
    private record Demand(UUID arena, Set<Key> chunks) {}
    /**
     * Shared native-ticket reference count and whether this broker actually added the plugin ticket.
     */
    private static final class Ticket {
        int references;
        final boolean added;
        /**
         * Records native ownership separately from the initial zero references.
         * @param added true only when native add created the plugin ticket
         */
        Ticket(boolean added) { this.added = added; }
    }
    /**
     * Narrow native-ticket boundary; tests inject failures and preexisting tickets without claiming real chunk ticking.
     */
    interface TicketAccess {
        /**
         * Adds this plugin's native ticket synchronously.
         * @param world native world
         * @param x chunk X
         * @param z chunk Z
         * @return true if added, false if already present; failures propagate
         */
        boolean add(World world, int x, int z);
        /**
         * Removes only a ticket this broker previously added.
         * @param world native world
         * @param x chunk X
         * @param z chunk Z
         */
        void remove(World world, int x, int z);
    }
    private final TicketAccess access;
    private boolean closed;
    private final Map<UUID, Region> regions = new HashMap<>();
    private final Map<UUID, Demand> demands = new HashMap<>();
    private final Map<Key, Ticket> tickets = new HashMap<>();
    private int reserved;
    /**
     * Binds the broker to the native plugin-ticket identity; no chunks are loaded at construction.
     * @param plugin non-null ticket owner
     */
    public ArenaTickets(Plugin plugin) {
        this(new TicketAccess() {
            /** Delegates to Paper's same-plugin ticket add operation.
 * @param world native world
 * @param x chunk X
 * @param z chunk Z
 * @return whether this call created the plugin ticket
 */ public boolean add(World world, int x, int z) { return world.addPluginChunkTicket(x, z, plugin); }
            /** Removes the broker-owned native ticket after the final reference.
 * @param world native world
 * @param x chunk X
 * @param z chunk Z
 */ public void remove(World world, int x, int z) { world.removePluginChunkTicket(x, z, plugin); }
        });
        Objects.requireNonNull(plugin);
    }
    /**
     * Injects ticket operations for deterministic ownership/rollback tests.
     * @param access non-null synchronous boundary
     */
    ArenaTickets(TicketAccess access) { this.access = Objects.requireNonNull(access); }
    /**
     * Reserves an arena's inclusive chunk footprint plus one neighbour margin before admitting shots. Does not load chunks.
     * @throws IllegalArgumentException for duplicate, invalid or over-capacity footprint
     * @throws IllegalStateException if off-thread or closed
     * @param arena unique generation UUID
     * @param world native world
     * @param bounds finite block-coordinate box within supported world limits
     */
    public void reserve(UUID arena, World world, Box bounds) {
        check(); if (regions.containsKey(arena)) throw new IllegalArgumentException("Duplicate ticket arena");
        int minX = chunk(bounds.min().x()) - 1, maxX = chunk(Math.nextDown(bounds.max().x())) + 1;
        int minZ = chunk(bounds.min().z()) - 1, maxZ = chunk(Math.nextDown(bounds.max().z())) + 1;
        long count = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        if (count <= 0 || count > MAX_RESERVED_CHUNKS - reserved) throw new IllegalArgumentException("Arena ticket reservation capacity reached");
        regions.put(arena, new Region(world, minX, maxX, minZ, maxZ, (int) count)); reserved += (int) count;
    }
    /**
     * Holds the centre chunk and eight neighbours. Acquires the new footprint before releasing the old; a failed acquisition rolls back only newly acquired references.
     * @throws IllegalArgumentException for outside coordinates or reusing an identity in another arena
     * @throws NullPointerException if the arena has no reservation
     * @param demandId stable consumer identity, such as a native arrow UUID
     * @param arena previously reserved generation
     * @param x centre chunk X inside the unpadded footprint
     * @param z centre chunk Z inside the unpadded footprint
     */
    public void retain(UUID demandId, UUID arena, int x, int z) {
        check(); Region region = Objects.requireNonNull(regions.get(arena), "Unreserved arena");
        if (x <= region.minX || x >= region.maxX || z <= region.minZ || z >= region.maxZ)
            throw new IllegalArgumentException("Demand outside arena footprint");
        Demand old = demands.get(demandId);
        if (old != null && !old.arena.equals(arena)) throw new IllegalArgumentException("Demand identity belongs to another arena");
        Set<Key> desired = new LinkedHashSet<>();
        for (int cx = x - 1; cx <= x + 1; cx++) for (int cz = z - 1; cz <= z + 1; cz++) desired.add(new Key(region.world, cx, cz));
        Set<Key> previous = old == null ? Set.of() : old.chunks;
        List<Key> acquired = new ArrayList<>();
        try {
            for (Key key : desired) if (!previous.contains(key)) {
                Ticket ticket = tickets.get(key);
                if (ticket == null) {
                    boolean added = access.add(key.world, key.x, key.z);
                    ticket = new Ticket(added); tickets.put(key, ticket);
                }
                ticket.references++; acquired.add(key);
            }
        } catch (RuntimeException failure) { acquired.forEach(this::releaseKey); throw failure; }
        for (Key key : previous) if (!desired.contains(key)) releaseKey(key);
        demands.put(demandId, new Demand(arena, Set.copyOf(desired)));
    }
    /**
     * Idempotently releases this consumer; shared and borrowed native tickets remain protected.
     * @param demandId stable consumer UUID
     */
    public void release(UUID demandId) {
        thread(); Demand demand = demands.remove(demandId);
        if (demand != null) demand.chunks.forEach(this::releaseKey);
    }
    /**
     * Releases all demands of this generation and its capacity charge.
     * @param arena generation UUID; absent generations are harmless
     */
    public void endArena(UUID arena) {
        thread(); for (var entry : List.copyOf(demands.entrySet())) if (entry.getValue().arena.equals(arena)) release(entry.getKey());
        Region region = regions.remove(arena); if (region != null) reserved -= region.count;
    }
    /**
     * Counts distinct currently referenced native chunk keys, including borrowed tickets.
     * @return shared chunk-key count
     */
    public int ticketCount() { thread(); return tickets.size(); }
    /**
     * Counts retained consumer neighbourhoods.
     * @return number of active demand identities
     */
    public int demandCount() { thread(); return demands.size(); }
    /**
     * Reads total potential footprint charged before admission.
     * @return reserved chunks, not necessarily loaded chunks
     */
    public int reservedCount() { thread(); return reserved; }
    /**
     * Drops one known reference and removes the native ticket only at zero and only if this broker added it.
     * @param key currently referenced native key
     */
    private void releaseKey(Key key) {
        Ticket ticket = tickets.get(key);
        if (--ticket.references == 0) {
            // An existing same-plugin ticket is borrowed, never adopted and later revoked.
            if (ticket.added) access.remove(key.world, key.x, key.z);
            tickets.remove(key);
        }
    }
    /**
     * Floors block coordinates before converting to chunks, including negative positions.
     * @throws IllegalArgumentException for unsupported coordinates
     * @param coordinate finite block coordinate within plus/minus 30,000,000
     * @return chunk coordinate
     */
    private static int chunk(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < -30_000_000 || coordinate > 30_000_000)
            throw new IllegalArgumentException("Arena outside supported world coordinates");
        return ((int) Math.floor(coordinate)) >> 4;
    }
    /**
     * Enforces native ticket operations on the classic Paper server thread.
     * @throws IllegalStateException if off-thread
     */
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tickets require server thread"); }
    /**
     * Requires the server thread and an open broker before new reservations/demands.
     * @throws IllegalStateException if off-thread or closed
     */
    private void check() { thread(); if (closed) throw new IllegalStateException("Tickets are closed"); }
    /**
     * Releases every owned demand/reservation on the server thread and closes admission. Native removal failures propagate; borrowed tickets are never removed.
     */
    @Override public void close() { thread(); for (UUID arena : List.copyOf(regions.keySet())) endArena(arena); closed = true; }
}
