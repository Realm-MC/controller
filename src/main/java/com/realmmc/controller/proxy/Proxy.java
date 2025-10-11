package com.realmmc.controller.proxy;

import com.google.inject.Inject;
import com.realmmc.controller.core.ControllerCore;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.proxy.ProxyModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(id = "controller", name = "Controller", description = "Core controller para RealmMC", version = "1.0.0", authors = {"onyell", "lucas"})
public class Proxy extends ControllerCore {

    @Getter
    private static Proxy instance;
    @Getter
    private final ProxyServer server;
    private final Path directory;
    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;

    @Getter
    private final ConcurrentHashMap<UUID, Long> loginTimestamps = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentHashMap<String, Boolean> premiumLoginStatus = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, UUID> offlineUuids = new ConcurrentHashMap<>();


    @Inject
    public Proxy(ProxyServer server, Logger logger, @DataDirectory Path directory) {
        super(logger);
        this.server = server;
        this.directory = directory;
        instance = this;
    }

    @Override
    public void initialize() {
        logger.info("Inicializando Controller Core (Proxy)...");
        initializeSharedServices();

        serviceRegistry = new ServiceRegistry(logger);
        moduleManager = new ModuleManager(logger);

        moduleManager.autoRegisterModules(AutoRegister.Platform.PROXY, getClass());
        moduleManager.registerModule(new SchedulerModule(server, this, logger));
        moduleManager.registerModule(new ProxyModule(server, this, logger));

        moduleManager.enableAllModules();

        startOnlineTimeBackupTask();
        logger.info("Controller Core (Proxy) inicializado com sucesso!");
    }

    @Override
    protected void initializeSharedServices() {
        super.initializeSharedServices();
        File messagesDir = new File(directory.toFile(), "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        MessagingSDK.getInstance().initializeForVelocity(messagesDir);
        logger.info("MessagingSDK inicializado para Velocity");
    }

    @Override
    public void shutdown() {
        logger.info("Finalizando Controller Core (Proxy)...");

        serviceRegistry.getService(StatisticsService.class).ifPresent(statsService -> {
            logger.info("Salvando tempo online...");
            server.getAllPlayers().forEach(player -> {
                Long loginTime = loginTimestamps.remove(player.getUniqueId());
                if (loginTime != null) {
                    long sessionDuration = System.currentTimeMillis() - loginTime;
                    statsService.addOnlineTime(player.getUniqueId(), sessionDuration);
                }
            });
            logger.info("Tempo online salvo.");
        });

        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }

        shutdownSharedServices();
        logger.info("Controller Core (Proxy) finalizado!");
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        initialize();
    }

    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        shutdown();
    }

    private void startOnlineTimeBackupTask() {
        StatisticsService statisticsService = serviceRegistry.getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não foi encontrado no registo!"));

        TaskScheduler.runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            if (loginTimestamps.isEmpty()) {
                return;
            }

            loginTimestamps.forEach((uuid, loginTime) -> {
                long sessionDuration = now - loginTime;
                statisticsService.addOnlineTime(uuid, sessionDuration);
                loginTimestamps.put(uuid, now);
            });
            logger.info("Tempo online salvo periodicamente para " + loginTimestamps.size() + " jogadores.");
        }, 5, 5, TimeUnit.MINUTES);
    }
}