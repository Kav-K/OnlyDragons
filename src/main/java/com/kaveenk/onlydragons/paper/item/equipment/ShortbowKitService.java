package com.kaveenk.onlydragons.paper.item.equipment;

import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * All-or-nothing capacity-preflighted development kit placement into empty storage.
 * It creates every bow before writing inventory and never replaces or drops existing
 * items. Permission checking belongs to the command; all work uses the server thread.
 */
public final class ShortbowKitService {
    /**
     * Ordinary arrows per kit, split into eight native stacks of 64.
     */
    public static final int ARROWS = 512;
    private final EquipmentStatsService equipment;

    /**
     * Uses the shared trusted equipment service for every generated weapon.
     * @param equipment loadout factory and post-grant snapshot authority
     */
    public ShortbowKitService(EquipmentStatsService equipment) { this.equipment = equipment; }

    /**
     * Places the seven declared bows and arrow stacks only when all fifteen slots fit.
     * Successful placement is followed by equipment refresh; arbitrary downstream API
     * exceptions are not an inventory transaction rollback mechanism.
     * @param player destination storage owner; permission is checked by the caller
     * @return false without mutation for insufficient slots, true after placement/refresh
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if a trusted loadout cannot be constructed
     */
    public boolean grant(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Kit grants require the server thread");
        var contents = player.getInventory().getStorageContents();
        var empty = new ArrayList<Integer>();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == null || contents[slot].getType().isAir()) empty.add(slot);
        }
        if (empty.size() < ShortbowLoadouts.ids().size() + ARROWS / 64) return false;
        var items = new ArrayList<ItemStack>();
        for (String id : ShortbowLoadouts.ids()) items.add(equipment.createLoadout(id));
        for (int i = 0; i < ARROWS / 64; i++) items.add(new ItemStack(Material.ARROW, 64));
        for (int i = 0; i < items.size(); i++) contents[empty.get(i)] = items.get(i);
        player.getInventory().setStorageContents(contents);
        equipment.refresh(player);
        return true;
    }
}
