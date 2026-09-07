package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;

/**
 * Production practice cow with normalized20 native HP and domain-owned damage.
 * No test companion is needed. Creation, health projection and removal run on the
 * server thread; managed combat owns cancellation and prevents native extra damage.
 */
public final class DummyBackend implements TargetBackend {
    private final Cow entity;
    /**
     * Spawns an inert nonpersistent cow at an already validated practice position.
     * @param location loaded world and unobstructed caller-selected block position
     */
    public DummyBackend(Location location) {
        entity = location.getWorld().spawn(location, Cow.class, cow -> {
            cow.setAI(false); cow.setGravity(false); cow.setPersistent(false);
            cow.setRemoveWhenFarAway(false); cow.setSilent(true);
            cow.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
            cow.setHealth(20); cow.setCustomNameVisible(true);
        });
    }
    /**
     * Returns the owned cow, including after removal for identity diagnostics.
     * @return same native entity created by this backend
     */
    public Cow entity() { return entity; }
    /**
     * Keeps ground practice targets outside Gravity's explicit category.
     * @return false, independent of vertical position
     */
    public boolean airborne() { return false; }
    /**
     * Mirrors domain health into the name and normalized native20-HP projection.
     * @param state authoritative current/max health, with positive maximum
     */
    public void synchronize(TargetState state) {
        entity.customName(Component.text("Practice | HP " + state.currentHealth() + " / " + state.maxHealth()));
        if (entity.isValid() && !entity.isDead()) entity.setHealth(20 * (state.currentHealth() / state.maxHealth()));
    }
    /**
     * Removes the dummy immediately after domain completion; no native death animation.
     * @param result already frozen result; not recalculated or granted here
     */
    public void defeated(EncounterResult result) { close(); }
    /**
     * Checks whether native removal has ended the cow's validity.
     * @return true once the owned cow is no longer valid
     */
    public boolean released() { return !entity.isValid(); }
    /**
     * Idempotently removes only this cow if still valid; it grants no loot or XP.
     */
    public void close() { if (entity.isValid()) entity.remove(); }
}
