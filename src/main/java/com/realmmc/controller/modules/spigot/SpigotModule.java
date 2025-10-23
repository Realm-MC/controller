package com.realmmc.controller.modules.spigot;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import com.realmmc.controller.spigot.sounds.SpigotSoundPlayer;
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
        return 45;
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SchedulerModule", "Command"};
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando comandos, listeners e sound player do Spigot...");

        try {
            SoundPlayer spigotSoundPlayer = new SpigotSoundPlayer();
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, spigotSoundPlayer);
            logger.info("SpigotSoundPlayer registrado com sucesso como SoundPlayer.");
        } catch (Exception e) {
            logger.severe("Falha ao registrar SpigotSoundPlayer: " + e.getMessage());
        }

        CommandManager.registerAll(plugin);
        ListenersManager.registerAll(plugin);
    }

    @Override
    protected void onDisable() {
        logger.info("Desregistrando comandos e sound player do Spigot...");

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        logger.info("SoundPlayer (Spigot) desregistrado.");

    }
}