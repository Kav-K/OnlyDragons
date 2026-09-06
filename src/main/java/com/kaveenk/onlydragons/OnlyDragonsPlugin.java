package com.kaveenk.onlydragons;

import com.kaveenk.onlydragons.command.DevCommand;
import com.kaveenk.onlydragons.listener.JoinListener;
import com.kaveenk.onlydragons.service.GreetingService;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentListener;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.stats.StatProfile;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalogLoader;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

// MockBukkit subclasses the entry point while loading it in tests.
public class OnlyDragonsPlugin extends JavaPlugin {
    private com.kaveenk.onlydragons.paper.encounter.ManagedCombatService combat;
    public com.kaveenk.onlydragons.paper.encounter.ManagedCombatService combat() { return combat; }
    private com.kaveenk.onlydragons.paper.projectile.OwnedBowService bows;
    public com.kaveenk.onlydragons.paper.projectile.OwnedBowService bows() { return bows; }
    private com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService dragons;
    public com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService dragons() { return dragons; }
    private GreetingService greetings;
    private EquipmentStatsService equipment;
    private DragonDefinitionRegistry dragonDefinitions;
    public DragonDefinitionRegistry dragonDefinitions() { return dragonDefinitions; }
    private EquipmentListener equipmentListener;
    public EquipmentStatsService equipment() { return equipment; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        var items = CalibrationLoadouts.compatibleRegistry();
        dragonDefinitions = new DragonDefinitionRegistry(DragonCatalogLoader.calibration(items));
        equipment = new EquipmentStatsService(
                items,
                StatProfile.calibration());
        bows = new com.kaveenk.onlydragons.paper.projectile.OwnedBowService(this, equipment, 2000, Math::random);
        getServer().getPluginManager().registerEvents(new com.kaveenk.onlydragons.paper.projectile.OwnedBowListener(bows), this);
        bows.start();
        combat = new com.kaveenk.onlydragons.paper.encounter.ManagedCombatService(this, bows);
        getServer().getPluginManager().registerEvents(new com.kaveenk.onlydragons.paper.encounter.ManagedCombatListener(combat), this);
        combat.start();
        dragons = new com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService(combat, dragonDefinitions,
                new com.kaveenk.onlydragons.paper.encounter.ArenaConfiguration(getDataFolder().toPath().resolve("config.yml"), dragonDefinitions), bows.continuity().tickets());
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
        try {
            try { if (dragons != null) dragons.close(); }
            finally { if (combat != null) combat.close(); }
        }
        finally {
            try { if (bows != null) bows.close(); }
            finally { if (equipmentListener != null) equipmentListener.close(); }
        }
        getLogger().info("OnlyDragons disabled");
    }
}
