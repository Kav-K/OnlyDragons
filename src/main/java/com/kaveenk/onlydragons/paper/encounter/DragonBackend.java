package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;

/** Real disposable dragon projection shared with future hatch controls. Own until native removal. */
public final class DragonBackend implements TargetBackend {
    private EnderDragon entity;
    private boolean lethal, announced, issuingLethal, disqualified, confirmed, removed;
    private final java.util.function.Consumer<EncounterResult> completion;
    private final java.util.function.Consumer<DragonBackend> retirement;
    private String outcome = "ALIVE";
    private EncounterResult result;
    private final com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets;
    private final java.util.UUID ticketOwner = java.util.UUID.randomUUID();
    public DragonBackend(Location location, com.kaveenk.onlydragons.paper.projectile.homing.ArenaTickets tickets, java.util.function.Consumer<EncounterResult> completion, java.util.function.Consumer<DragonBackend> retirement) {
        this.tickets = tickets; this.completion = completion; this.retirement = retirement;
        try {
            var min = new com.kaveenk.onlydragons.domain.projectile.Vector3(location.getX() - 16, location.getY() - 16, location.getZ() - 16);
            var max = new com.kaveenk.onlydragons.domain.projectile.Vector3(location.getX() + 16, location.getY() + 16, location.getZ() + 16);
            tickets.reserve(ticketOwner, location.getWorld(), new com.kaveenk.onlydragons.domain.projectile.homing.TracerRules.Box(min, max));
            tickets.retain(ticketOwner, ticketOwner, location.getBlockX() >> 4, location.getBlockZ() >> 4);
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
    public boolean airborne() { return true; }
    public void synchronize(TargetState state) {
        if (lethal) return;
        entity.customName(Component.text("Test Dragon | HP " + state.currentHealth() + " / " + state.maxHealth()));
        if (state.currentHealth() == 0) {
            outcome = "NATIVE_DEATH_PENDING";
            lethal = true; // Set before reentrant native death event. Never retry a cancelled death.
            entity.setAI(true); // Native death animation must advance.
            issuingLethal = true;
            try { entity.setHealth(0); }
            finally { issuingLethal = false; }
            confirmed = !disqualified && outcome.equals("ANIMATING") && entity.getHealth() == 0;
            if (!confirmed) { disqualified = true; if (!outcome.equals("DEATH_CANCELLED")) outcome = "NATIVE_MISMATCH"; }
        } else entity.setHealth(200 * state.currentHealth() / state.maxHealth());
    }
    public void defeated(EncounterResult result) { this.result = result; if (disqualified) retirement.accept(this); else announce(); }
    public boolean announcesImmediately() { return false; }
    public String outcome() { return outcome; }
    public void deathObserved(boolean cancelled) {
        if (!lethal) return;
        if (disqualified || !issuingLethal) { disqualified = true; return; }
        if (cancelled) { disqualified = true; outcome = "DEATH_CANCELLED"; }
        else outcome = "ANIMATING";
    }
    public void removed(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        removed = true;
        if (cause != org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH) disqualified = true;
        outcome = "REMOVED_" + cause; retirement.accept(this);
    }
    private void announce() {
        if (announced || disqualified || !confirmed || result == null || !outcome.equals("ANIMATING") || removed || entity.getHealth() != 0) return;
        announced = true; completion.accept(result);
    }
    public boolean released() {
        // isValid() also tests liveness: a zero-HP dragon still owns its native animation.
        if (removed) tickets.endArena(ticketOwner);
        return removed;
    }
    public boolean lethalRequested() { return lethal; }
    public void close() {
        try { if (entity != null && !removed) { disqualified = true; outcome = "RESET"; entity.remove(); removed = true; } }
        finally { tickets.endArena(ticketOwner); retirement.accept(this); }
    }
}
