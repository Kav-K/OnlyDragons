package com.kaveenk.onlydragons.domain.projectile;

import java.util.*;

/** Creating-thread owned capacity and terminal-claim authority. No entity references. */
public final class ArrowRegistry {
    /**
     * Creating-thread owner; no method transfers ownership or schedules work.
     */
    private final Thread thread = Thread.currentThread();
    /**
     * Combined bound on emitted arrows and outstanding group slots, including claimed arrows.
     */
    private final int capacity;
    /**
     * Remaining un-emitted slots per accepted trigger group.
     */
    private final Map<UUID, Integer> reservations = new HashMap<>();
    /**
     * Immutable emitted captures retained in insertion order until explicit retirement.
     */
    private final Map<UUID, OwnedProjectile> arrows = new LinkedHashMap<>();
    /**
     * Terminally claimed arrow IDs; claim does not release capacity until retirement.
     */
    private final Set<UUID> claimed = new HashSet<>();
    /**
     * Creates an empty registry with positive combined reserved/emitted capacity. All instance
     * operations enforce this constructing thread; adapters own entity removal and scheduler cleanup.
     */
    public ArrowRegistry(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }
    /**
     * Atomically reserves an entire group of one primary plus optional Duplex. Returns false
     * without mutation if its group is already pending/emitted or capacity is insufficient.
     * @param group nonnull accepted trigger UUID
     * @param count 1 or 2 total physical slots
     * @return whether the full reservation was installed
     */
    public boolean reserve(UUID group, int count) {
        check(); Objects.requireNonNull(group);
        if (count < 1 || count > 2) throw new IllegalArgumentException("One primary plus optional Duplex");
        if (reservations.containsKey(group) || arrows.values().stream().anyMatch(a -> a.shot().shotId().equals(group)))
            return false;
        if (used() > capacity - count) return false;
        reservations.put(group, count); return true;
    }
    /**
     * Converts one reserved slot into an immutable emitted arrow without increasing used capacity.
     * Missing group reservation or duplicate current projectile UUID throws IllegalStateException.
     * The caller owns actual native spawning/rollback; no entity is created here.
     */
    public void emit(OwnedProjectile arrow) {
        check(); UUID group = arrow.shot().shotId(), id = arrow.shot().projectileId();
        int remaining = reservations.getOrDefault(group, 0);
        if (remaining == 0 || arrows.containsKey(id)) throw new IllegalStateException("No unique reserved arrow");
        arrows.put(id, arrow);
        if (remaining == 1) reservations.remove(group); else reservations.put(group, remaining - 1);
    }
    /**
     * Retains its slot and identity until terminal retirement, even when the candidate will reject.
     * <p>
     * Returns the owned capture only for the first terminal claim of a registered UUID; unknown
     * or repeated claims return empty. Rejected collisions still require explicit retire to release capacity.
     */
    public Optional<OwnedProjectile> claim(UUID id) {
        check(); var arrow = arrows.get(id);
        return arrow != null && claimed.add(id) ? Optional.of(arrow) : Optional.empty();
    }
    /**
     * Idempotently removes one emitted arrow and terminal claim. Does not remove native entities
     * or cancel another still-reserved sibling; call release for remaining group reservations.
     */
    public void retire(UUID id) { check(); arrows.remove(id); claimed.remove(id); }
    /**
     * Idempotently releases un-emitted group slots only; already emitted arrows remain owned.
     */
    public void release(UUID group) { check(); reservations.remove(group); }
    /**
     * Returns the immutable registered capture or empty, including after a claim until retirement.
     */
    public Optional<OwnedProjectile> lookup(UUID id) { check(); return Optional.ofNullable(arrows.get(id)); }
    /**
     * Returns an immutable emitted-arrow snapshot in insertion order, including terminally claimed entries.
     */
    public List<OwnedProjectile> snapshot() { check(); return List.copyOf(arrows.values()); }
    /**
     * Returns emitted count plus remaining reserved slots; claimed entries still consume capacity.
     */
    public int used() { check(); return arrows.size() + reservations.values().stream().mapToInt(Integer::intValue).sum(); }
    /**
     * Returns slots not yet emitted; reservations are accounted separately from live arrow identity.
     */
    public int reserved() { check(); return used() - arrows.size(); }
    /**
     * Clears all captures, reservations and claims on the owner thread; caller separately retires
     * native arrows/tickets. This registry has no closed flag and may be reused after clear.
     */
    public void clear() { check(); reservations.clear(); arrows.clear(); claimed.clear(); }
    /**
     * Throws IllegalStateException outside the constructing thread; no locks or server imports are needed.
     */
    private void check() { if (Thread.currentThread() != thread) throw new IllegalStateException("Registry requires owner thread"); }
}
