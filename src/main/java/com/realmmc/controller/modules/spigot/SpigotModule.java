package com.realmmc.controller.modules.spigot;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class SpigotModule extends AbstractCoreModule {
    private final Plugin plugin;

    public SpigotModule(Plugin plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "SpigotModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo específico para funcionalidades do Spigot";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SchedulerModule", "Command"};
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando comandos e listeners do Spigot...");
        CommandManager.registerAll(plugin);
        ListenersManager.registerAll(plugin);
    }

    @Override
    protected void onDisable() {
        logger.info("Desregistrando comandos do Spigot...");
        CommandManager.unregisterAll(plugin);
    }
}