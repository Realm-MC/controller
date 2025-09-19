package com.realmmc.controller.modules.scheduler;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class SchedulerModule extends AbstractCoreModule {
    private Object server;
    private Object plugin;

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
        return 100;
    }

    @Override
    public String[] getDependencies() {
        return new String[]{};
    }

    @Override
    protected void onEnable() {
        logger.info("Inicializando TaskScheduler...");
        if (server instanceof ProxyServer) {
            TaskScheduler.init((ProxyServer) server, plugin);
        } else {
            logger.info("TaskScheduler não é necessário no ambiente Spigot");
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