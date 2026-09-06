package com.kaveenk.onlydragons;

import com.kaveenk.onlydragons.command.DevCommand;
import com.kaveenk.onlydragons.listener.JoinListener;
import com.kaveenk.onlydragons.service.GreetingService;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentListener;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.stats.StatProfile;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

// MockBukkit subclasses the entry point while loading it in tests.
public class OnlyDragonsPlugin extends JavaPlugin {
    private GreetingService greetings;
    private EquipmentStatsService equipment;
    private EquipmentListener equipmentListener;
    public EquipmentStatsService equipment() { return equipment; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        equipment = new EquipmentStatsService(
                CalibrationLoadouts.registry(),
                StatProfile.calibration());
        equipmentListener = new EquipmentListener(this, equipment);
        getServer().getPluginManager().registerEvents(equipmentListener, this);
        getServer().getOnlinePlayers().forEach(equipmentListener::queue);
        var command = Objects.requireNonNull(getCommand("onlydragons"), "Missing onlydragons command");
        var handler = new DevCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getLogger().info("OnlyDragons enabled");
    }

    public void reloadSettings() {
        reloadConfig();
        greetings = new GreetingService(getConfig().getString("welcome-message", "Welcome, {player}!"));
    }

    public GreetingService greetings() {
        return greetings;
    }

    @Override
    public void onDisable() {
        if (equipmentListener != null) equipmentListener.close();
        getLogger().info("OnlyDragons disabled");
    }
}
