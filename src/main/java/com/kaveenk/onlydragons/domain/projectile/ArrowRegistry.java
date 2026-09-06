package com.kaveenk.onlydragons.domain.projectile;

import java.util.*;

/** Creating-thread owned capacity and terminal-claim authority. No entity references. */
public final class ArrowRegistry {
    private final Thread thread = Thread.currentThread();
    private final int capacity;
    private final Map<UUID, Integer> reservations = new HashMap<>();
    private final Map<UUID, OwnedProjectile> arrows = new LinkedHashMap<>();
    private final Set<UUID> claimed = new HashSet<>();
    public ArrowRegistry(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }
    public boolean reserve(UUID group, int count) {
        check(); Objects.requireNonNull(group);
        if (count < 1 || count > 2) throw new IllegalArgumentException("One primary plus optional Duplex");
        if (reservations.containsKey(group) || arrows.values().stream().anyMatch(a -> a.shot().shotId().equals(group)))
            return false;
        if (used() > capacity - count) return false;
        reservations.put(group, count); return true;
    }
    public void emit(OwnedProjectile arrow) {
        check(); UUID group = arrow.shot().shotId(), id = arrow.shot().projectileId();
        int remaining = reservations.getOrDefault(group, 0);
        if (remaining == 0 || arrows.containsKey(id)) throw new IllegalStateException("No unique reserved arrow");
        arrows.put(id, arrow);
        if (remaining == 1) reservations.remove(group); else reservations.put(group, remaining - 1);
    }
    /** Retains its slot and identity until terminal retirement, even when the candidate will reject. */
    public Optional<OwnedProjectile> claim(UUID id) {
        check(); var arrow = arrows.get(id);
        return arrow != null && claimed.add(id) ? Optional.of(arrow) : Optional.empty();
    }
    public void retire(UUID id) { check(); arrows.remove(id); claimed.remove(id); }
    public void release(UUID group) { check(); reservations.remove(group); }
    public Optional<OwnedProjectile> lookup(UUID id) { check(); return Optional.ofNullable(arrows.get(id)); }
    public List<OwnedProjectile> snapshot() { check(); return List.copyOf(arrows.values()); }
    public int used() { check(); return arrows.size() + reservations.values().stream().mapToInt(Integer::intValue).sum(); }
    public int reserved() { check(); return used() - arrows.size(); }
    public void clear() { check(); reservations.clear(); arrows.clear(); claimed.clear(); }
    private void check() { if (Thread.currentThread() != thread) throw new IllegalStateException("Registry requires owner thread"); }
}
