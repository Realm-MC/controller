package com.realmmc.controller.spigot;

import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.realmmc.controller.spigot.display.DisplayItemService;
import com.realmmc.controller.spigot.display.config.DisplayConfigLoader;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    @Getter
    private static DisplayItemService displayItemService;
    @Getter
    private static DisplayConfigLoader displayConfigLoader;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        PacketEvents.getAPI().init();
        displayItemService = new DisplayItemService();
        saveDefaultConfigFiles();
        displayConfigLoader = new DisplayConfigLoader(this);
        displayConfigLoader.load();
        CommandManager.registerAll(this);
        ListenersManager.registerAll(this);
        getLogger().info("Controller (Spigot) enabled! " + getDescription().getVersion());
    }
    @Override
    public void onDisable() {
        CommandManager.unregisterAll(this);
        try { PacketEvents.getAPI().terminate(); } catch (Exception ignored) {}
        getLogger().info("Controller (Spigot) disabled! " + getDescription().getVersion());
        instance = null;
    }

    private void saveDefaultConfigFiles() {
        if (getResource("displays.yml") != null) {
            saveResource("displays.yml", false);
        }
    }
}
