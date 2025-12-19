package com.palacesky.controller.modules.logger;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.File;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class LogModule extends AbstractCoreModule {

    private LogService logService;
    private RealmLogAppender appender;

    public LogModule(java.util.logging.Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "LogModule"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public String getDescription() { return "Sistema de logs unificado (Caixa Preta)"; }

    @Override public String[] getDependencies() { return new String[]{"SchedulerModule"}; }

    @Override
    protected void onEnable() throws Exception {
        File dataFolder;
        String serverName;

        if (isSpigot()) {
            Class<?> loaderClass = Class.forName("com.palacesky.controller.modules.logger.SpigotLogLoader");
            dataFolder = (File) loaderClass.getMethod("getDataFolder").invoke(null);
            serverName = (String) loaderClass.getMethod("getServerName").invoke(null);
        } else {
            Class<?> loaderClass = Class.forName("com.palacesky.controller.modules.logger.ProxyLogLoader");
            dataFolder = (File) loaderClass.getMethod("getDataFolder").invoke(null);
            serverName = (String) loaderClass.getMethod("getServerName").invoke(null);
        }

        String envName = System.getProperty("controller.serverId");
        if (envName != null) serverName = envName;

        this.logService = new LogService(logger, dataFolder, serverName);
        ServiceRegistry.getInstance().registerService(LogService.class, logService);

        try {
            Object rootObj = LogManager.getRootLogger();
            if (rootObj instanceof Logger) {
                Logger rootLogger = (Logger) rootObj;
                this.appender = new RealmLogAppender(logService);
                this.appender.start();
                rootLogger.addAppender(appender);
                logger.info("Interceptador de Console (Log4j) ativado.");
            }
        } catch (Throwable e) {
            logger.warning("Log4j não disponível ou incompatível: " + e.getMessage());
        }

        if (isSpigot()) {
            Class<?> loaderClass = Class.forName("com.palacesky.controller.modules.logger.SpigotLogLoader");
            loaderClass.getMethod("load", LogService.class).invoke(null, logService);
        } else {
            Class<?> loaderClass = Class.forName("com.palacesky.controller.modules.logger.ProxyLogLoader");
            loaderClass.getMethod("load", LogService.class).invoke(null, logService);
        }

        logger.info("LogService iniciado. Sessão: " + logService.getSessionCode());
    }

    @Override
    protected void onDisable() {
        try {
            if (appender != null) {
                Object rootObj = LogManager.getRootLogger();
                if (rootObj instanceof Logger) {
                    ((Logger) rootObj).removeAppender(appender);
                }
                appender.stop();
            }
        } catch (Throwable ignored) {}

        if (logService != null) {
            logService.shutdown();
            ServiceRegistry.getInstance().unregisterService(LogService.class);
        }
    }

    private boolean isSpigot() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}