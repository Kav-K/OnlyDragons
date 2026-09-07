package com.kaveenk.onlydragons.gametests.projectile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Native collision calibration with explicitly selected cancellation controls.
 * The fixture records LOWEST/MONITOR hit and damage ordering for registered arrows;
 * its HIGHEST listeners inject the named veto controls. It does not invoke the
 * production damage engine or synthesize collision events.
 */
public final class ImpactProbe implements Listener {
    /**
     * Per-arrow trial policy; ZERO_DAMAGE is configured on the arrow by its caller.
     */
    enum Mode { NATIVE, CANCEL_HIT, CANCEL_DAMAGE, ZERO_DAMAGE }
    final Map<UUID, Mode> arrows = new LinkedHashMap<>();
    final List<Map<String, Object>> events = new ArrayList<>();

    /**
     * Records the incoming native hit, including actual multipart parent identity.
     * @param event real projectile-hit dispatch
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void hitFirst(ProjectileHitEvent event) { hit(event, "hit_lowest"); }
    /**
     * Applies only the registered CANCEL_HIT trial's deliberate native veto.
     * @param event real projectile-hit dispatch
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hitCancel(ProjectileHitEvent event) {
        if (arrows.get(event.getEntity().getUniqueId()) == Mode.CANCEL_HIT) event.setCancelled(true);
    }
    /**
     * Records the hit cancellation state after the trial veto listener.
     * @param event real projectile-hit dispatch
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void hitLast(ProjectileHitEvent event) { hit(event, "hit_monitor"); }
    /**
     * Records native damage before the trial's damage veto.
     * @param event real entity damage dispatch
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void damageFirst(EntityDamageByEntityEvent event) { damage(event, "damage_lowest"); }
    /**
     * Applies only the registered CANCEL_DAMAGE trial's deliberate veto.
     * @param event real entity damage dispatch
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damageCancel(EntityDamageByEntityEvent event) {
        if (arrows.get(event.getDamager().getUniqueId()) == Mode.CANCEL_DAMAGE) event.setCancelled(true);
    }
    /**
     * Records native raw/final damage and cancellation at MONITOR.
     * @param event real entity damage dispatch
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void damageLast(EntityDamageByEntityEvent event) { damage(event, "damage_monitor"); }

    /**
     * Records only admitted arrow hits, keeping blocks distinct from entity/part contacts.
     */
    private void hit(ProjectileHitEvent event, String stage) {
        UUID arrow = event.getEntity().getUniqueId();
        if (!arrows.containsKey(arrow)) return;
        var row = base(arrow, stage, event.getHitEntity(), event.isCancelled());
        row.put("block", event.getHitBlock() == null ? "none" : event.getHitBlock().getType().name());
        events.add(row);
    }
    /**
     * Records only admitted direct arrow damagers without assigning production credit.
     */
    private void damage(EntityDamageByEntityEvent event, String stage) {
        UUID arrow = event.getDamager().getUniqueId();
        if (!arrows.containsKey(arrow)) return;
        var row = base(arrow, stage, event.getEntity(), event.isCancelled());
        row.put("damage", event.getDamage());
        row.put("finalDamage", event.getFinalDamage());
        events.add(row);
    }
    /**
     * Builds the common ordered row, preserving part UUID and dragon-parent UUID separately.
     */
    private Map<String, Object> base(UUID arrow, String stage, Entity hit, boolean cancelled) {
        var row = new LinkedHashMap<String, Object>();
        row.put("sequence", events.size());
        row.put("tick", Bukkit.getCurrentTick());
        row.put("arrow", arrow.toString());
        row.put("stage", stage);
        row.put("cancelled", cancelled);
        row.put("entity", hit == null ? "none" : hit.getUniqueId().toString());
        row.put("type", hit == null ? "none" : hit.getType().name());
        row.put("partName", hit == null ? "none" : hit.getName());
        row.put("parent", hit instanceof EnderDragonPart part ? part.getParent().getUniqueId().toString() : "none");
        return row;
    }
    /**
     * Selects one arrow and dispatch stage for trial assertions.
     * @param arrow registered real projectile UUID
     * @param stage exact probe stage name
     * @return matching rows in observation order
     */
    List<Map<String, Object>> forArrow(UUID arrow, String stage) {
        return events.stream().filter(e -> e.get("arrow").equals(arrow.toString()) && e.get("stage").equals(stage)).toList();
    }
}
