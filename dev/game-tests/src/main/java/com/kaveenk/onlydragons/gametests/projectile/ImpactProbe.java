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

/** Measurement only: no production engine and no synthetic event dispatch. */
public final class ImpactProbe implements Listener {
    enum Mode { NATIVE, CANCEL_HIT, CANCEL_DAMAGE, ZERO_DAMAGE }
    final Map<UUID, Mode> arrows = new LinkedHashMap<>();
    final List<Map<String, Object>> events = new ArrayList<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void hitFirst(ProjectileHitEvent event) { hit(event, "hit_lowest"); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hitCancel(ProjectileHitEvent event) {
        if (arrows.get(event.getEntity().getUniqueId()) == Mode.CANCEL_HIT) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void hitLast(ProjectileHitEvent event) { hit(event, "hit_monitor"); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void damageFirst(EntityDamageByEntityEvent event) { damage(event, "damage_lowest"); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damageCancel(EntityDamageByEntityEvent event) {
        if (arrows.get(event.getDamager().getUniqueId()) == Mode.CANCEL_DAMAGE) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void damageLast(EntityDamageByEntityEvent event) { damage(event, "damage_monitor"); }

    private void hit(ProjectileHitEvent event, String stage) {
        UUID arrow = event.getEntity().getUniqueId();
        if (!arrows.containsKey(arrow)) return;
        var row = base(arrow, stage, event.getHitEntity(), event.isCancelled());
        row.put("block", event.getHitBlock() == null ? "none" : event.getHitBlock().getType().name());
        events.add(row);
    }
    private void damage(EntityDamageByEntityEvent event, String stage) {
        UUID arrow = event.getDamager().getUniqueId();
        if (!arrows.containsKey(arrow)) return;
        var row = base(arrow, stage, event.getEntity(), event.isCancelled());
        row.put("damage", event.getDamage());
        row.put("finalDamage", event.getFinalDamage());
        events.add(row);
    }
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
    List<Map<String, Object>> forArrow(UUID arrow, String stage) {
        return events.stream().filter(e -> e.get("arrow").equals(arrow.toString()) && e.get("stage").equals(stage)).toList();
    }
}
