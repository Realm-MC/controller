package com.realmmc.controller.modules.proxy;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
import com.realmmc.controller.proxy.sounds.VelocitySoundPlayer;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.logging.Logger;

public class ProxyModule extends AbstractCoreModule {
    private final ProxyServer server;
    private final Object plugin;

    public ProxyModule(ProxyServer server, Object plugin, Logger logger) {
        super(logger);
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "ProxyModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo específico para funcionalidades do proxy";
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
        logger.info("Registrando comandos, listeners e sound player do proxy...");

        try {
            SoundPlayer velocitySoundPlayer = new VelocitySoundPlayer();
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, velocitySoundPlayer);
            logger.info("VelocitySoundPlayer registrado com sucesso como SoundPlayer.");
        } catch (Exception e) {
            logger.severe("Falha ao registrar VelocitySoundPlayer: " + e.getMessage());
        }

        CommandManager.registerAll(plugin);
        ListenersManager.registerAll(server, plugin);
    }

    @Override
    protected void onDisable() {
        logger.info("Desregistrando comandos e sound player do proxy...");

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        logger.info("SoundPlayer (Velocity) desregistrado.");

    }
}