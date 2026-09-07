package com.kaveenk.onlydragons;

import com.kaveenk.onlydragons.command.DevCommand;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonCatalogLoader;
import com.kaveenk.onlydragons.domain.encounter.definition.DragonDefinitionRegistry;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.stats.StatProfile;
import com.kaveenk.onlydragons.listener.JoinListener;
import com.kaveenk.onlydragons.paper.encounter.ArenaConfiguration;
import com.kaveenk.onlydragons.paper.encounter.DevelopmentDragonService;
import com.kaveenk.onlydragons.paper.encounter.ManagedCombatListener;
import com.kaveenk.onlydragons.paper.encounter.ManagedCombatService;
import com.kaveenk.onlydragons.paper.encounter.presentation.DragonHealthPresenter;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentListener;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import com.kaveenk.onlydragons.paper.item.anvil.CustomAnvilService;
import com.kaveenk.onlydragons.paper.item.anvil.CustomAnvilListener;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowListener;
import com.kaveenk.onlydragons.paper.projectile.OwnedBowService;
import com.kaveenk.onlydragons.service.GreetingService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

// MockBukkit subclasses the entry point while loading it in tests.
public class OnlyDragonsPlugin extends JavaPlugin {
    private CustomAnvilService anvils;
    public CustomAnvilService anvils() { return anvils; }
    private ManagedCombatService combat;
    public ManagedCombatService combat() {
        return combat;
    }

    private OwnedBowService bows;
    public OwnedBowService bows() {
        return bows;
    }

    private DevelopmentDragonService dragons;
    public DevelopmentDragonService dragons() {
        return dragons;
    }

    private DragonHealthPresenter dragonHealth;
    public DragonHealthPresenter dragonHealth() {
        return dragonHealth;
    }

    private GreetingService greetings;
    private EquipmentStatsService equipment;
    private DragonDefinitionRegistry dragonDefinitions;
    public DragonDefinitionRegistry dragonDefinitions() {
        return dragonDefinitions;
    }

    private EquipmentListener equipmentListener;
    public EquipmentStatsService equipment() {
        return equipment;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        var items = CalibrationLoadouts.compatibleRegistry();
        dragonDefinitions = new DragonDefinitionRegistry(DragonCatalogLoader.calibration(items));
        equipment = new EquipmentStatsService(
                items,
                StatProfile.calibration());
        anvils = new CustomAnvilService(this, items);
        getServer().getPluginManager().registerEvents(new CustomAnvilListener(anvils), this);
        bows = new OwnedBowService(this, equipment, 2000, Math::random);
        getServer().getPluginManager().registerEvents(new OwnedBowListener(bows), this);
        bows.start();
        combat = new ManagedCombatService(this, bows);
        getServer().getPluginManager().registerEvents(new ManagedCombatListener(combat), this);
        combat.start();
        dragons = new DevelopmentDragonService(combat, dragonDefinitions,
                new ArenaConfiguration(getDataFolder().toPath().resolve("config.yml"), dragonDefinitions), bows.continuity().tickets());
        dragonHealth = new DragonHealthPresenter(this, dragons, combat);
        dragonHealth.start();
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
            if (anvils != null) anvils.close();
        } finally {
            closeCombatServices();
        }
        getLogger().info("OnlyDragons disabled");
    }

    private void closeCombatServices() {
        try {
            try {
                try {
                    if (dragonHealth != null) {
                        dragonHealth.close();
                    }
                } finally {
                    if (dragons != null) {
                        dragons.close();
                    }
                }
            } finally {
                if (combat != null) {
                    combat.close();
                }
            }
        } finally {
            try {
                if (bows != null) {
                    bows.close();
                }
            } finally {
                if (equipmentListener != null) {
                    equipmentListener.close();
                }
            }
        }
    }
}
