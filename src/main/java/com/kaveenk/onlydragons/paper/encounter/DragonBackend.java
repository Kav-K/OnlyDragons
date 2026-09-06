package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;

/** Real disposable dragon projection shared with future hatch controls. Own until native removal. */
public final class DragonBackend implements TargetBackend {
    private EnderDragon entity;
    private final DragonFlight flight;
    private boolean lethal, announced, issuingLethal, disqualified, confirmed, removed, retired;
    private final java.util.function.Consumer<EncounterResult> completion;
    private final java.util.function.Consumer<DragonBackend> retirement;
    private String outcome = "ALIVE";
    private EncounterResult result;
    private final com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets;
    private final java.util.UUID ticketOwner = java.util.UUID.randomUUID();
    public DragonBackend(Location location, com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets, java.util.function.Consumer<EncounterResult> completion, java.util.function.Consumer<DragonBackend> retirement) {
        this(location, org.bukkit.util.BoundingBox.of(location, 16, 16, 16), DragonFlight.Mode.STATIONARY, tickets, completion, retirement);
    }
    public DragonBackend(Location location, org.bukkit.util.BoundingBox bounds, DragonFlight.Mode mode,
                         com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets,
                         java.util.function.Consumer<EncounterResult> completion, java.util.function.Consumer<DragonBackend> retirement) {
        this.tickets = tickets; this.completion = completion; this.retirement = retirement;
        flight = new DragonFlight(location, bounds, mode);
        try {
            var min = new com.kaveenk.onlydragons.domain.projectile.Vector3(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
            var max = new com.kaveenk.onlydragons.domain.projectile.Vector3(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
            tickets.reserve(ticketOwner, location.getWorld(), new com.kaveenk.onlydragons.domain.projectile.homing.TracerRules.Box(min, max));
            // Hold every route-center chunk neighbourhood before movement, including native death.
            // This avoids crossing into chunks still waiting to become entity-ticking.
            int minX = ((int)Math.floor(location.getX()-flight.radius())) >> 4;
            int maxX = ((int)Math.floor(location.getX()+flight.radius())) >> 4;
            int minZ = ((int)Math.floor(location.getZ()-flight.radius())) >> 4;
            int maxZ = ((int)Math.floor(location.getZ()+flight.radius())) >> 4;
            for (int x=minX; x<=maxX; x++) for (int z=minZ; z<=maxZ; z++)
                tickets.retain(java.util.UUID.randomUUID(), ticketOwner, x, z);
            entity = location.getWorld().spawn(location, EnderDragon.class, dragon -> {
                entity = dragon; // Retain even if initialization or a spawn listener fails.
                dragon.setPersistent(false); dragon.setRemoveWhenFarAway(false);
                dragon.setAI(true); dragon.setGravity(false); dragon.setPhase(EnderDragon.Phase.HOVER);
                dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
                dragon.setHealth(200);
            });
            if (!entity.isValid() || entity.isDead() || entity.getDragonBattle() != null)
                throw new IllegalArgumentException("Dragon initialization rejected or attached to a native battle.");
        } catch (RuntimeException failure) { close(); throw failure; }
    }
    public EnderDragon entity() { return entity; }
    public DragonFlight.View motion() { return flight.view(); }
    public boolean airborne() { return true; }
    public void synchronize(TargetState state) {
        if (lethal) return;
        entity.customName(Component.text("Test Dragon | HP " + state.currentHealth() + " / " + state.maxHealth()));
        if (state.currentHealth() == 0) {
            flight.stop(); entity.setVelocity(new org.bukkit.util.Vector());
            outcome = "NATIVE_DEATH_PENDING";
            lethal = true; // Set before reentrant native death event. Never retry a cancelled death.
            entity.setAI(true); // Native death animation must advance.
            issuingLethal = true;
            try { entity.setHealth(0); }
            finally { issuingLethal = false; }
            confirmed = !disqualified && outcome.equals("ANIMATING") && entity.getHealth() == 0;
            if (!confirmed) { disqualified = true; if (!outcome.equals("DEATH_CANCELLED")) outcome = "NATIVE_MISMATCH"; }
        } else { entity.setHealth(200 * state.currentHealth() / state.maxHealth()); flight.tick(entity); }
    }
    public void defeated(EncounterResult result) { this.result = result; if (disqualified) retire(); else announce(); }
    public boolean announcesImmediately() { return false; }
    public String outcome() { return outcome; }
    public void deathObserved(boolean cancelled) {
        if (!lethal) return;
        if (disqualified || !issuingLethal) { disqualified = true; return; }
        if (cancelled) { disqualified = true; outcome = "DEATH_CANCELLED"; }
        else outcome = "ANIMATING";
    }
    public void removed(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        flight.stop(); removed = true;
        if (cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH) disqualified = true;
        outcome = "REMOVED_" + cause; retire();
    }
    private void announce() {
        if (announced || disqualified || !confirmed || result == null || !outcome.equals("ANIMATING") || actuallyRemoved() || entity.getHealth() != 0) return;
        announced = true; completion.accept(result);
    }
    private void retire() {
        if (retired) return;
        retired = true;
        retirement.accept(this);
    }
    private boolean actuallyRemoved() {
        // The event arrives before Paper assigns its removal reason; retain both observations.
        return removed || entity == null || entity.getRemovalReason() != null;
    }
    public boolean released() {
        // isValid() also tests liveness: a zero-HP dragon still owns its native animation.
        if (actuallyRemoved()) { tickets.endArena(ticketOwner); retire(); return true; }
        return false;
    }
    public boolean lethalRequested() { return lethal; }
    public void close() {
        flight.stop();
        try { if (entity != null && !actuallyRemoved()) { disqualified = true; outcome = "RESET"; entity.remove(); removed = true; } }
        finally { tickets.endArena(ticketOwner); retire(); }
    }
}
