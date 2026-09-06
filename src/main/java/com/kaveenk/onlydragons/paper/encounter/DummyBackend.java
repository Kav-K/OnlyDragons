package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;

/** Disposable production practice target; no test companion dependency. */
public final class DummyBackend implements TargetBackend {
    private final Cow entity;
    public DummyBackend(Location location) {
        entity = location.getWorld().spawn(location, Cow.class, cow -> {
            cow.setAI(false); cow.setGravity(false); cow.setPersistent(false);
            cow.setRemoveWhenFarAway(false); cow.setSilent(true);
            cow.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
            cow.setHealth(20); cow.setCustomNameVisible(true);
        });
    }
    public Cow entity() { return entity; }
    public boolean airborne() { return false; }
    public void synchronize(TargetState state) {
        entity.customName(Component.text("Practice | HP " + state.currentHealth() + " / " + state.maxHealth()));
        if (entity.isValid() && !entity.isDead()) entity.setHealth(20 * (state.currentHealth() / state.maxHealth()));
    }
    public void defeated(EncounterResult result) { close(); }
    public boolean released() { return !entity.isValid(); }
    public void close() { if (entity.isValid()) entity.remove(); }
}
