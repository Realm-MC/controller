package com.palacesky.controller.modules.scheduler;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.ProxyServer; // Import Velocity
import org.bukkit.plugin.Plugin; // Import Spigot

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
        try {
            Class<?> proxyServerClass = Class.forName("com.velocitypowered.api.proxy.ProxyServer");
            if (server != null && proxyServerClass.isInstance(server)) {
                TaskScheduler.init((ProxyServer) server, plugin);
                logger.info("TaskScheduler inicializado para Velocity.");
                return;
            }
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class<?> spigotPluginClass = Class.forName("org.bukkit.plugin.Plugin");
            if (plugin != null && spigotPluginClass.isInstance(plugin)) {
                TaskScheduler.init((Plugin) plugin);
                logger.info("TaskScheduler inicializado para Spigot.");
                return;
            }
        } catch (ClassNotFoundException ignored) {
        }

        logger.warning("Tipo de servidor/plugin não reconhecido para TaskScheduler: server=" + (server != null ? server.getClass().getName() : "null") + ", plugin=" + (plugin != null ? plugin.getClass().getName() : "null"));
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