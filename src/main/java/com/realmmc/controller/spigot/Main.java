package com.realmmc.controller.spigot;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import io.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.realmmc.controller.spigot.display.DisplayItemService;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    @Getter
    private static DisplayItemService displayItemService;

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
}
