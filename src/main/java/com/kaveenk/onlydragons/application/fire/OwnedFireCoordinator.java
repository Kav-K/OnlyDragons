package com.kaveenk.onlydragons.application.fire;

import com.kaveenk.onlydragons.application.proc.ProcCoordinator.Session;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.CombatEncounter;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One bounded per-generation owner-thread effect store, drained by the existing combat tick. */
public final class OwnedFireCoordinator implements AutoCloseable {
    public record Source(ShotContext shot, DamageResult damage, long collisionTick, Session session, int level) {}
    public record Outcome(DamageResult damage, Source source) {}
    public record Metrics(int burns, int vulnerabilities, int sessions) {}
    private record Burn(Source source, double potency, long next, long end, long order) {}
    private record Vulnerability(double multiplier, long end) {}
    private final Thread owner = Thread.currentThread();
    private final CombatEncounter encounter;
    private final int capacity;
    private final Set<Session> sessions = new HashSet<>();
    private final Map<Session, Burn> burns = new HashMap<>();
    private final Map<Session, Vulnerability> vulnerabilities = new HashMap<>();
    private long sequence, lastDrain = -1;
    private boolean closed;

    public OwnedFireCoordinator(CombatEncounter encounter, int capacity) {
        this.encounter = Objects.requireNonNull(encounter);
        if (capacity <= 0) throw new IllegalArgumentException("Invalid fire capacity");
        this.capacity = capacity;
    }
    public void activate(Session session) {
        thread(); if (closed) return;
        if (!sessions.contains(session) && sessions.size() >= capacity) throw new IllegalStateException("Fire session capacity");
        sessions.add(session);
    }
    public void clearSession(Session session) {
        thread(); sessions.remove(session); burns.remove(session); vulnerabilities.remove(session);
    }
    public Metrics metrics() { thread(); return new Metrics(burns.size(), vulnerabilities.size(), sessions.size()); }
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
    private void expire(long now) {
        vulnerabilities.values().removeIf(v -> v.end() <= now);
        burns.values().removeIf(b -> b.end() < now && b.next() > b.end());
    }
    @Override public void close() { thread(); closed = true; burns.clear(); vulnerabilities.clear(); sessions.clear(); }
    private void thread() { if (Thread.currentThread() != owner) throw new IllegalStateException("Fire accessed outside owner thread"); }
}
