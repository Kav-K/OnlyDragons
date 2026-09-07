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
     * @param capacity positive maximum of emitted arrows plus reserved slots
     * @throws IllegalArgumentException if capacity is not positive
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
     * @param arrow immutable real-arrow provenance whose group has a reserved slot
     * @throws IllegalStateException if off the constructing thread, unreserved or already emitted under the same UUID
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
     * @param id registered native projectile UUID to claim once
     * @return owned capture for the first claim, or empty for unknown/repeated claims
     */
    public Optional<OwnedProjectile> claim(UUID id) {
        check(); var arrow = arrows.get(id);
        return arrow != null && claimed.add(id) ? Optional.of(arrow) : Optional.empty();
    }
    /**
     * Idempotently removes one emitted arrow and terminal claim. Does not remove native entities
     * or cancel another still-reserved sibling; call release for remaining group reservations.
     * @param id native projectile UUID; absent entries are harmless
     */
    public void retire(UUID id) { check(); arrows.remove(id); claimed.remove(id); }
    /**
     * Idempotently releases un-emitted group slots only; already emitted arrows remain owned.
     * @param group firing group UUID whose un-emitted slots should be released
     */
    public void release(UUID group) { check(); reservations.remove(group); }
    /**
     * Returns the immutable registered capture or empty, including after a claim until retirement.
     * @param id native projectile UUID to inspect
     * @return immutable capture, including a claimed entry until retirement; empty if absent
     */
    public Optional<OwnedProjectile> lookup(UUID id) { check(); return Optional.ofNullable(arrows.get(id)); }
    /**
     * Returns an immutable emitted-arrow snapshot in insertion order, including terminally claimed entries.
     * @return immutable emitted-arrow list in insertion order, including claimed entries
     */
    public List<OwnedProjectile> snapshot() { check(); return List.copyOf(arrows.values()); }
    /**
     * Returns emitted count plus remaining reserved slots; claimed entries still consume capacity.
     * @return emitted-arrow count plus all remaining reserved slots
     */
    public int used() { check(); return arrows.size() + reservations.values().stream().mapToInt(Integer::intValue).sum(); }
    /**
     * Returns slots not yet emitted; reservations are accounted separately from live arrow identity.
     * @return number of reserved slots not yet converted to emitted arrows
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
