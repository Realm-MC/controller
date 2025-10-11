package com.realmmc.controller.modules.scheduler;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.ProxyServer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class SchedulerModule extends AbstractCoreModule {
    private final Object server;
    private final Object plugin;

    public SchedulerModule(Object server, Object plugin, Logger logger) {
        super(logger);
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "SchedulerModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo de agendamento de tarefas";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    protected void onEnable() {
        logger.info("Inicializando TaskScheduler...");
        if (server instanceof ProxyServer) {
            TaskScheduler.init((ProxyServer) server, plugin);
        } else if (server instanceof Plugin) {
            TaskScheduler.init((Plugin) server);
        } else {
            logger.warning("Tipo de servidor não reconhecido para TaskScheduler: " + server.getClass().getName());
        }
    }

    @Override
    protected void onDisable() {
        logger.info("Finalizando TaskScheduler...");
        try {
            TaskScheduler.shutdown();
        } catch (Exception e) {
            logger.warning("Erro ao finalizar TaskScheduler: " + e.getMessage());
        }
    }
}