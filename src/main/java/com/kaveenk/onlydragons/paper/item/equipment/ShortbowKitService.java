package com.kaveenk.onlydragons.paper.item.equipment;

import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** All-or-nothing development grant into empty storage slots; never replaces or drops items. */
public final class ShortbowKitService {
    public static final int ARROWS = 512;
    private final EquipmentStatsService equipment;

    public ShortbowKitService(EquipmentStatsService equipment) { this.equipment = equipment; }

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
