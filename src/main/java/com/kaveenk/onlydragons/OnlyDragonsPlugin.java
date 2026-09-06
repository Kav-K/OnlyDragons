package com.kaveenk.onlydragons;

import com.kaveenk.onlydragons.command.DevCommand;
import com.kaveenk.onlydragons.listener.JoinListener;
import com.kaveenk.onlydragons.service.GreetingService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

// MockBukkit subclasses the entry point while loading it in tests.
public class OnlyDragonsPlugin extends JavaPlugin {
    private GreetingService greetings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
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
        getLogger().info("OnlyDragons disabled");
    }
}
