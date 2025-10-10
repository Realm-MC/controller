package com.realmmc.controller.modules.proxy;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
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
        return 50;
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SchedulerModule", "Command"};
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando comandos e listeners do proxy...");
        CommandManager.registerAll(plugin);
        ListenersManager.registerAll(server, plugin);
    }

    @Override
    protected void onDisable() {
        logger.info("Desregistrando comandos do proxy...");
    }
}