package com.realmmc.controller.spigot;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;

    @Override
    public void onEnable() {
        instance = this;

        CommandManager.registerAll(this);
        ListenersManager.registerAll(this);
        getLogger().info("Controller (Spigot) enabled! " + getDescription().getVersion());
    }
    @Override
    public void onDisable() {
        CommandManager.unregisterAll(this);
        getLogger().info("Controller (Spigot) disabled! " + getDescription().getVersion());
        instance = null;
    }
}
