package com.kaveenk.onlydragons.paper.projectile.homing;

import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules.Box;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** One broker for all same-plugin chunk demands. Consumers use distinct demand IDs, never raw removal. */
public final class ArenaTickets implements AutoCloseable {
    // Reservation is potential footprint, not eagerly loaded chunks. Existing broad calibration arenas fit.
    public static final int MAX_RESERVED_CHUNKS = 32768;
    private record Region(World world, int minX, int maxX, int minZ, int maxZ, int count) {}
    private record Key(World world, int x, int z) {}
    private record Demand(UUID arena, Set<Key> chunks) {}
    private static final class Ticket {
        int references;
        final boolean added;
        Ticket(boolean added) { this.added = added; }
    }
    interface TicketAccess {
        boolean add(World world, int x, int z);
        void remove(World world, int x, int z);
    }
    private final TicketAccess access;
    private boolean closed;
    private final Map<UUID, Region> regions = new HashMap<>();
    private final Map<UUID, Demand> demands = new HashMap<>();
    private final Map<Key, Ticket> tickets = new HashMap<>();
    private int reserved;
    public ArenaTickets(Plugin plugin) {
        this(new TicketAccess() {
            public boolean add(World world, int x, int z) { return world.addPluginChunkTicket(x, z, plugin); }
            public void remove(World world, int x, int z) { world.removePluginChunkTicket(x, z, plugin); }
        });
        Objects.requireNonNull(plugin);
    }
    ArenaTickets(TicketAccess access) { this.access = Objects.requireNonNull(access); }
    public void reserve(UUID arena, World world, Box bounds) {
        check(); if (regions.containsKey(arena)) throw new IllegalArgumentException("Duplicate ticket arena");
        int minX = chunk(bounds.min().x()) - 1, maxX = chunk(Math.nextDown(bounds.max().x())) + 1;
        int minZ = chunk(bounds.min().z()) - 1, maxZ = chunk(Math.nextDown(bounds.max().z())) + 1;
        long count = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        if (count <= 0 || count > MAX_RESERVED_CHUNKS - reserved) throw new IllegalArgumentException("Arena ticket reservation capacity reached");
        regions.put(arena, new Region(world, minX, maxX, minZ, maxZ, (int) count)); reserved += (int) count;
    }
    /** Holds the current chunk and its eight neighbours. Reservation guarantees room before shot admission. */
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
    public void release(UUID demandId) {
        thread(); Demand demand = demands.remove(demandId);
        if (demand != null) demand.chunks.forEach(this::releaseKey);
    }
    public void endArena(UUID arena) {
        thread(); for (var entry : List.copyOf(demands.entrySet())) if (entry.getValue().arena.equals(arena)) release(entry.getKey());
        Region region = regions.remove(arena); if (region != null) reserved -= region.count;
    }
    public int ticketCount() { thread(); return tickets.size(); }
    public int demandCount() { thread(); return demands.size(); }
    public int reservedCount() { thread(); return reserved; }
    private void releaseKey(Key key) {
        Ticket ticket = tickets.get(key);
        if (--ticket.references == 0) {
            // An existing same-plugin ticket is borrowed, never adopted and later revoked.
            if (ticket.added) access.remove(key.world, key.x, key.z);
            tickets.remove(key);
        }
    }
    private static int chunk(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < -30_000_000 || coordinate > 30_000_000)
            throw new IllegalArgumentException("Arena outside supported world coordinates");
        return ((int) Math.floor(coordinate)) >> 4;
    }
    private static void thread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tickets require server thread"); }
    private void check() { thread(); if (closed) throw new IllegalStateException("Tickets are closed"); }
    @Override public void close() { thread(); for (UUID arena : List.copyOf(regions.keySet())) endArena(arena); closed = true; }
}
