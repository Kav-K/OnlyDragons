package com.kaveenk.onlydragons.application.fire;

import com.kaveenk.onlydragons.application.proc.ProcCoordinator.Session;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.CombatEncounter;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One bounded per-generation owner-thread effect store, drained by the existing combat tick.
 * <p>
 * All instance access is confined to the constructing thread. The caller supplies the same
 * thread as {@link CombatEncounter}, drives Ferocity before fire, clears old session tokens
 * on replacement, and closes effects on reset/disable. This store admits one burn and one
 * vulnerability per exact session; it does not independently enforce one token per player.
 * No listeners, scheduler tasks, native ignition or damage authority are added.
 */
public final class OwnedFireCoordinator implements AutoCloseable {
    /**
     * Retained independently of prunable Ferocity diagnostics; this record performs no validation.
     * @param shot exact captured physical shot
     * @param damage accepted physical/Duplex result used as the potency basis
     * @param collisionTick original adapter collision time for diagnostics, not the burn clock
     * @param session exact captured player lifecycle
     * @param level Flame I/II of the retained strongest source
     */
    public record Source(ShotContext shot, DamageResult damage, long collisionTick, Session session, int level) {}
    /**
     * An already-evaluated strike paired with its retained physical provenance.
     * @param damage authoritative encounter result; do not apply it again
     * @param source immutable source retained by the burn
     */
    public record Outcome(DamageResult damage, Source source) {}
    /**
     * Current bounded state counts, without advancing time.
     * @param burns retained session burns
     * @param vulnerabilities retained session vulnerability records
     * @param sessions activated exact session tokens
     */
    public record Metrics(int burns, int vulnerabilities, int sessions) {}
    /**
     * Immutable refresh/drain replacement; weaker refresh retains source and potency.
     * @param source strongest source, latest on equal potency
     * @param potency accepted physical credit times Flame fraction, before vulnerability
     * @param next next due game tick, retained across refresh
     * @param end latest refresh plus duration, inclusive for the final due strike
     * @param order monotonically allocated creation order, retained across refresh
     */
    private record Burn(Source source, double potency, long next, long end, long order) {}
    /**
     * One owner's latest Duplex vulnerability; strongest active owner wins globally.
     * @param multiplier captured level factor in 1.1–1.5
     * @param end exclusive expiry game tick
     */
    private record Vulnerability(double multiplier, long end) {}
    /**
     * Creating thread; all mutable fire operations remain on this thread.
     */
    private final Thread owner = Thread.currentThread();
    private final CombatEncounter encounter;
    /**
     * Positive bound applied independently to active sessions, burns and vulnerabilities.
     */
    private final int capacity;
    /**
     * Exact activated tokens; unlike proc activation, this set does not replace old tokens by UUID.
     */
    private final Set<Session> sessions = new HashSet<>();
    /**
     * One strongest retained burn per session, with cadence preserved across refresh.
     */
    private final Map<Session, Burn> burns = new HashMap<>();
    /**
     * Latest vulnerability per session; evaluation selects the strongest unexpired multiplier globally.
     */
    private final Map<Session, Vulnerability> vulnerabilities = new HashMap<>();
    /**
     * Creation-order allocator and last consumed drain tick; refresh retains a burn’s original order.
     */
    private long sequence, lastDrain = -1;
    /**
     * Terminal effect lifecycle flag; closing this coordinator does not end the encounter.
     */
    private boolean closed;

    /**
     * Retains a nonnull encounter and positive per-store/session capacity. It captures this thread
     * but does not verify the encounter's thread until later access; construct both on the server thread.
     */
    public OwnedFireCoordinator(CombatEncounter encounter, int capacity) {
        this.encounter = Objects.requireNonNull(encounter);
        if (capacity <= 0) throw new IllegalArgumentException("Invalid fire capacity");
        this.capacity = capacity;
    }
    /**
     * Adds an exact session token idempotently; throws IllegalStateException for a new token at
     * capacity. Closed activation is inert. Caller must clear old tokens on reconnect; this set
     * does not replace other sessions sharing the UUID.
     */
    public void activate(Session session) {
        thread(); if (closed) return;
        if (!sessions.contains(session) && sessions.size() >= capacity) throw new IllegalStateException("Fire session capacity");
        sessions.add(session);
    }
    /**
     * Removes only the supplied exact session's activation, burn and vulnerability; idempotent
     * and permitted after close on the creating thread. Other owners/tokens remain intact.
     */
    public void clearSession(Session session) {
        thread(); sessions.remove(session); burns.remove(session); vulnerabilities.remove(session);
    }
    /**
     * Returns retained counts on the creating thread, including after close; no expiry side effects.
     */
    public Metrics metrics() { thread(); return new Metrics(burns.size(), vulnerabilities.size(), sessions.size()); }
    /**
     * Consumes an already-committed physical result. Closed/inactive/dead/rejected inputs do
     * nothing; mismatched kind/identity throws. Accepted Duplex can refresh vulnerability without
     * igniting. Flame refresh extends end, preserves cadence/order, and adopts source only when
     * equal or stronger. This operation is not an all-field transaction: expiry/vulnerability may
     * change before a later validation/arithmetic exception, so the integration closes on failure.
     * @param shot nonnull trusted captured shot matching the accepted damage
     * @param damage accepted physical/Duplex result; encounter revalidates exact source at fire commit
     * @param collision original collision tick retained as diagnostics
     * @param session exact currently activated captured session
     */
    public void physical(ShotContext shot, DamageResult damage, long collision, Session session) {
        thread();
        if (closed || !sessions.contains(session) || !encounter.target().alive() || !damage.accepted()) return;
        if ((damage.kind() != DamageResult.Kind.PHYSICAL && damage.kind() != DamageResult.Kind.DUPLEX)
                || !damage.ownerId().equals(session.ownerId()) || !shot.ownerId().equals(session.ownerId())
                || !shot.shotId().equals(damage.shotId()) || !shot.projectileId().equals(damage.origin().projectileId())
                || !encounter.target().encounterId().equals(damage.origin().encounterId())
                || !encounter.target().targetId().equals(damage.origin().targetId()))
            throw new IllegalArgumentException("Effect source identity mismatch");
        long now = damage.tick();
        expire(now);
        int duplex = EnchantEffects.level(shot.enchantments(), "duplex", 5);
        if (damage.kind() == DamageResult.Kind.DUPLEX && duplex > 0)
            vulnerabilities.put(session, new Vulnerability(QuiverFlameProfile.vulnerability(duplex),
                    Math.addExact(now, QuiverFlameProfile.VULNERABILITY_DURATION)));
        int level = EnchantEffects.level(shot.enchantments(), "flame", 2);
        if (level == 0) return;
        double potency = damage.amounts().contributionDamage() * QuiverFlameProfile.fireFraction(level);
        var source = new Source(shot, damage, collision, session, level);
        Burn old = burns.get(session);
        // A burn's final due boundary is inclusive. Refresh never postpones the original cadence.
        burns.put(session, new Burn(old != null && old.potency() > potency ? old.source() : source,
                old == null ? potency : Math.max(old.potency(), potency),
                old == null ? Math.addExact(now, QuiverFlameProfile.FIRE_SPACING) : old.next(),
                Math.addExact(now, QuiverFlameProfile.FIRE_DURATION), old == null ? ++sequence : old.order()));
    }
    /**
     * Requires a strictly advancing nonnegative drain tick even after close. Due burns drain by
     * next tick then creation order, at most one strike per burn per call; overdue strikes survive.
     * Each strike samples the strongest unexpired vulnerability and is removed before commit.
     * Exceptions propagate with prior commits preserved and the failed burn consumed; callers must
     * reconcile/close instead of retrying blindly. Target death clears all effects without minting
     * another completion.
     * @param now receiver game tick, strictly greater than the prior drain
     * @return immutable evaluated outcomes in drain order; empty after close
     */
    public List<Outcome> tick(long now) {
        thread();
        if (now < 0 || now <= lastDrain) throw new IllegalArgumentException("Fire drain must advance");
        lastDrain = now;
        if (closed) return List.of();
        if (!encounter.target().alive()) { close(); return List.of(); }
        // Do not drop overdue strikes before draining. At most one strike per active burn per server tick.
        vulnerabilities.values().removeIf(v -> v.end() <= now);
        var due = burns.values().stream().filter(b -> b.next() <= now && b.next() <= b.end())
                .sorted(Comparator.comparingLong(Burn::next).thenComparingLong(Burn::order)).toList();
        var results = new ArrayList<Outcome>();
        for (var burn : due) {
            if (!encounter.target().alive()) { close(); break; }
            Session session = burn.source().session();
            burns.remove(session); // Consume before commit: failures cannot retry the strike.
            if (!sessions.contains(session)) continue;
            double vulnerability = vulnerabilities.values().stream().mapToDouble(Vulnerability::multiplier).max().orElse(1);
            var source = burn.source();
            UUID id = UUID.nameUUIDFromBytes(("fire:" + encounter.target().encounterId() + ":" + burn.order() + ":" + burn.next()).getBytes(StandardCharsets.UTF_8));
            var command = new FireCommand(id, source.damage(), source.level(), vulnerability, burn.next());
            var result = encounter.fire(command, now);
            results.add(new Outcome(result, source));
            long next = Math.addExact(burn.next(), QuiverFlameProfile.FIRE_SPACING);
            if (result.accepted() && encounter.target().alive() && next <= burn.end())
                burns.put(session, new Burn(source, burn.potency(), next, burn.end(), burn.order()));
        }
        if (!encounter.target().alive()) close();
        return List.copyOf(results);
    }
    /**
     * Expires vulnerability at its exclusive boundary. A burn is removed only after end and after
     * its last eligible due tick, so late drains do not silently lose pending strikes.
     */
    private void expire(long now) {
        vulnerabilities.values().removeIf(v -> v.end() <= now);
        burns.values().removeIf(b -> b.end() < now && b.next() > b.end());
    }
    /**
     * Idempotently clears sessions, burns and vulnerabilities and stops new effect admission.
     * Does not end the shared CombatEncounter; its lifecycle owner closes combat separately.
     */
    @Override public void close() { thread(); closed = true; burns.clear(); vulnerabilities.clear(); sessions.clear(); }
    /**
     * Rejects any access outside the constructing thread with IllegalStateException.
     */
    private void thread() { if (Thread.currentThread() != owner) throw new IllegalStateException("Fire accessed outside owner thread"); }
}
