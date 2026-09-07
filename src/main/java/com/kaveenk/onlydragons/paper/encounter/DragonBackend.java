package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;

/**
 * Public-API disposable dragon projection shared with later hatch controls.
 * Owns native AI-enabled HOVER geometry, normalized 200 HP, bounded route demands and
 * one lethal request. Domain completion precedes native HP zero; a cancelled or
 * administrative death cannot publish a board. Ownership/tickets persist through
 * native animation until actual removal, which is not equivalent to isValid=false.
 * All access is caller-confined to the classic Paper server thread.
 * @see TargetBackend
 * @see DevelopmentDragonService
 */
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
    /**
     * Constructs the legacy stationary backend inside a radius 16 cube.
     * @param location loaded-world spawn center in blocks
     * @param tickets shared broker, distinct demand owner allocated by this backend
     * @param completion callback after frozen result plus confirmed uncancelled native death
     * @param retirement once-only presentation-retirement callback
     * @throws RuntimeException for ticket/spawn/native validation failure; owned cleanup is attempted
     */
    public DragonBackend(Location location, com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets, java.util.function.Consumer<EncounterResult> completion, java.util.function.Consumer<DragonBackend> retirement) {
        this(location, org.bukkit.util.BoundingBox.of(location, 16, 16, 16), DragonFlight.Mode.STATIONARY, tickets, completion, retirement);
    }
    /**
     * Reserves route neighborhoods before spawning a nonpersistent native dragon.
     * Retains the native reference inside the spawn consumer for failure cleanup.
     * Rejects cancelled initialization or attachment to a vanilla dragon battle.
     * @param location loaded-world center in blocks
     * @param bounds full admissible arena cube, copied by the route controller
     * @param mode stationary or bounded public-position orbit
     * @param tickets plugin-wide reference-counted ticket broker
     * @param completion frozen-result callback, not another damage authority
     * @param retirement once-only callback when presentation eligibility retires
     * @throws RuntimeException on admission/native setup failure after attempting cleanup
     */
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
    /**
     * Exposes the same parent UUID through liveness and death animation.
     * @return owned dragon; never a replacement spawned to conceal removal
     */
    public EnderDragon entity() { return entity; }
    /**
     * Reads the backend-owned route without moving the entity.
     * @return immutable mode/state/radius/step diagnostics
     */
    public DragonFlight.View motion() { return flight.view(); }
    /**
     * Declares the semantic Gravity target category independently of native phase.
     * @return true; physical phase admission remains separately enforced
     */
    public boolean airborne() { return true; }
    /**
     * Mirrors domain HP to the native 200-HP scale or requests lethal exactly once.
     * At zero, stops flight before setHealth(0), marks lethal before event reentry and
     * confirms the uncancelled ANIMATING outcome afterward. Cancelled death is never retried.
     * @param state authoritative domain health with positive maximum
     * @throws RuntimeException if native projection or route bounds fail; combat owns retirement
     */
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
    /**
     * Retains frozen accounting and attempts notification only after native confirmation.
     * @param result immutable result already committed before native lethal projection
     */
    public void defeated(EncounterResult result) { this.result = result; if (disqualified) retire(); else announce(); }
    /**
     * Requires the confirmed native transition instead of domain-zero-only announcements.
     * @return false
     */
    public boolean announcesImmediately() { return false; }
    /**
     * Reads the native lifecycle diagnostic, separate from domain state.
     * @return ALIVE, pending/animation/cancellation/mismatch/reset/removal outcome text
     */
    public String outcome() { return outcome; }
    /**
     * Accepts final cancellation state only during this backend's own lethal call.
     * Later/administrative death observations disqualify normal notification permanently.
     * @param cancelled final native death-event cancellation, not arrow suppression
     */
    public void deathObserved(boolean cancelled) {
        if (!lethal) return;
        if (disqualified || !issuingLethal) { disqualified = true; return; }
        if (cancelled) { disqualified = true; outcome = "DEATH_CANCELLED"; }
        else outcome = "ANIMATING";
    }
    /**
     * Records the immediate public removal event before Paper assigns removal reason.
     * Stops route/presentation; broker release remains in released/close.
     * @param cause actual native removal cause; non-DEATH disqualifies completion delivery
     */
    public void removed(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        flight.stop(); removed = true;
        if (cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH) disqualified = true;
        outcome = "REMOVED_" + cause; retire();
    }
    /**
     * Marks notification before callback; requires frozen result, zero native HP and confirmed live ANIMATING ownership.
     */
    private void announce() {
        if (announced || disqualified || !confirmed || result == null || !outcome.equals("ANIMATING") || actuallyRemoved() || entity.getHealth() != 0) return;
        announced = true; completion.accept(result);
    }
    /**
     * Calls presentation retirement at most once, independently from ticket release.
     */
    private void retire() {
        if (retired) return;
        retired = true;
        retirement.accept(this);
    }
    /**
     * Combines immediate removal-event state with the later public removal reason.
     * @return true after actual removal, including partial initialization with no entity
     */
    private boolean actuallyRemoved() {
        // The event arrives before Paper assigns its removal reason; retain both observations.
        return removed || entity == null || entity.getRemovalReason() != null;
    }
    /**
     * Releases broker ownership only after actual native removal, then retires presentation.
     * @return false for a zero-HP dragon still animating; true once resources are released
     */
    public boolean released() {
        // isValid() also tests liveness: a zero-HP dragon still owns its native animation.
        if (actuallyRemoved()) { tickets.endArena(ticketOwner); retire(); return true; }
        return false;
    }
    /**
     * Reports the irreversible one-shot native lethal boundary.
     * @return true once setHealth(0) was requested, even if its death was cancelled
     */
    public boolean lethalRequested() { return lethal; }
    /**
     * Stops movement, removes only the owned native entity and releases broker demands.
     * Ticket/presentation cleanup is attempted in finally even if native removal throws.
     * Reset/disable cannot produce a legitimate defeat notification.
     */
    public void close() {
        flight.stop();
        try { if (entity != null && !actuallyRemoved()) { disqualified = true; outcome = "RESET"; entity.remove(); removed = true; } }
        finally { tickets.endArena(ticketOwner); retire(); }
    }
}
