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
/**
 * Composition root for the classic-Paper plugin: trusted catalogs feed equipment,
 * anvils and one projectile/combat pipeline, followed by dragon controls and UI.
 * Lifecycle callbacks and returned mutable services belong to the server thread.
 * Services are not safe to use before enable or after disable; getters expose the
 * owned instances, not new factories. MockBukkit subclasses this entry point.
 * @see OwnedBowService
 * @see ManagedCombatService
 */
public class OnlyDragonsPlugin extends JavaPlugin {
    private CustomAnvilService anvils;
    /**
     * Returns the plugin-owned native-anvil transaction guard.
     * @return service after its enable-time construction, or null before construction
     */
    public CustomAnvilService anvils() { return anvils; }
    private ManagedCombatService combat;
    /**
     * Returns the sole receiver of settled physical claims and owner of managed fights.
     * @return combat service after construction, or null before construction
     */
    public ManagedCombatService combat() {
        return combat;
    }

    private OwnedBowService bows;
    /**
     * Returns the firing, physical-claim and native-suppression authority.
     * @return owned bow service after construction, or null before construction
     */
    public OwnedBowService bows() {
        return bows;
    }

    private DevelopmentDragonService dragons;
    /**
     * Returns the configured single-development-dragon control surface.
     * @return development service after construction, or null before construction
     */
    public DevelopmentDragonService dragons() {
        return dragons;
    }

    private DragonHealthPresenter dragonHealth;
    /**
     * Returns the read-only domain-health presentation owner.
     * @return presenter after construction, or null before construction
     */
    public DragonHealthPresenter dragonHealth() {
        return dragonHealth;
    }

    private GreetingService greetings;
    private EquipmentStatsService equipment;
    private DragonDefinitionRegistry dragonDefinitions;
    /**
     * Returns the shared registry whose full selections are captured by new encounters.
     * @return registry after construction, or null before construction
     */
    public DragonDefinitionRegistry dragonDefinitions() {
        return dragonDefinitions;
    }

    private EquipmentListener equipmentListener;
    /**
     * Returns the equipment boundary used to revalidate items at shot acceptance.
     * @return equipment service after construction, or null before construction
     */
    public EquipmentStatsService equipment() {
        return equipment;
    }

    /**
     * Builds the service graph and registers synchronous listeners, loops and commands.
     * Catalog validation precedes gameplay admission; the combat receiver is attached
     * to the existing bow owner rather than registering a second damage path.
     * @throws IllegalStateException if a constructed component rejects its lifecycle
     * @throws NullPointerException if the required command is absent from plugin.yml
     */
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

    /**
     * Reloads configuration and replaces only the plain greeting formatter.
     * Call on the server thread. This does not replace item/stat catalogs, active
     * encounter selections or already captured arrows.
     */
    public void reloadSettings() {
        reloadConfig();
        greetings = new GreetingService(getConfig().getString("welcome-message", "Welcome, {player}!"));
    }

    /**
     * Returns the most recently loaded immutable greeting formatter.
     * @return current formatter, or null before the first settings load
     */
    public GreetingService greetings() {
        return greetings;
    }

    /**
     * Closes anvil callbacks, then attempts every gameplay owner in dependency order.
     * Nested finally blocks keep a failing close from skipping later cleanup; an
     * exception can still propagate after those attempts. No result or reward is minted.
     */
    @Override
    public void onDisable() {
        try {
            if (anvils != null) anvils.close();
        } finally {
            closeCombatServices();
        }
        getLogger().info("OnlyDragons disabled");
    }

    /**
     * Detaches health viewers before native dragon/combat retirement, then releases
     * projectiles and equipment callbacks. Each later close is attempted even if an
     * earlier owner throws; partially constructed enable state is tolerated.
     */
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
