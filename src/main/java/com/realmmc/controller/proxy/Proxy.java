package com.realmmc.controller.proxy;

import com.google.inject.Inject;
import com.realmmc.controller.core.ControllerCore;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.proxy.ProxyModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.shared.geoip.GeoIPService;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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
    private GeoIPService geoIPService;

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
        try {
            logger.info("Inicializando Controller Core (Proxy)...");

            serviceRegistry = new ServiceRegistry(logger);
            initializeSharedServices();
            moduleManager = new ModuleManager(logger);

            moduleManager.autoRegisterModules(AutoRegister.Platform.PROXY, getClass());
            moduleManager.registerModule(new SchedulerModule(server, this, logger));
            moduleManager.registerModule(new ProxyModule(server, this, logger));

            moduleManager.enableAllModules();

            startOnlineTimeBackupTask();

            logger.info("Controller Core (Proxy) inicializado com sucesso!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro fatal durante a inicialização do Controller no Proxy!", e);
        }
    }

    @Override
    protected void initializeSharedServices() {
        super.initializeSharedServices();

        try {
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
            }
            Path messagesPath = directory.resolve("messages");
            if (Files.notExists(messagesPath)) {
                Files.createDirectories(messagesPath);
            }

            copyResourceIfNotExists("messages/pt_BR.properties", messagesPath.resolve("pt_BR.properties"));
            copyResourceIfNotExists("messages/en.properties", messagesPath.resolve("en.properties"));

            geoIPService = new GeoIPService(directory.toFile(), logger);
            if (serviceRegistry != null) {
                serviceRegistry.registerService(GeoIPService.class, geoIPService);
                logger.info("Serviço registrado: GeoIPService");
            } else {
                logger.severe("ServiceRegistry não foi inicializado antes de registrar GeoIPService!");
            }

            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForVelocity(messagesPath.toFile());
                logger.info("MessagingSDK inicializado para Velocity");
            } else {
                logger.warning("Tentativa de reinicializar MessagingSDK ignorada.");
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Falha ao inicializar serviços compartilhados (mensagens/GeoIP) no Proxy!", e);
        }
    }

    private void copyResourceIfNotExists(String resourcePath, Path targetPath) throws IOException {
        if (Files.notExists(targetPath)) {
            try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
                if (stream == null) {
                    logger.warning("Recurso não encontrado no JAR: " + resourcePath);
                    return;
                }
                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Ficheiro padrão copiado para: " + targetPath);
            }
        }
    }

    @Override
    public void shutdown() {
        try {
            logger.info("Finalizando Controller Core (Proxy)...");

            ServiceRegistry currentRegistry = ServiceRegistry.getInstance();

            if (currentRegistry != null) {
                currentRegistry.getService(StatisticsService.class).ifPresent(statsService -> {
                    logger.info("Salvando tempo online...");
                    server.getAllPlayers().forEach(player -> {
                        Long loginTime = loginTimestamps.remove(player.getUniqueId());
                        if (loginTime != null) {
                            long sessionDuration = System.currentTimeMillis() - loginTime;
                            if (sessionDuration > 0) {
                                statsService.addOnlineTime(player.getUniqueId(), sessionDuration);
                            }
                        }
                    });
                    logger.info("Tempo online salvo.");
                });
            }

            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

        } finally {
            if (geoIPService != null) {
                geoIPService.close();
            }
            ServiceRegistry currentRegistry = ServiceRegistry.getInstance();
            if (currentRegistry != null) {
                currentRegistry.unregisterService(GeoIPService.class);
            }
            MessagingSDK.getInstance().shutdown();
            shutdownSharedServices();
            logger.info("Controller Core (Proxy) finalizado.");
        }
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
        if (serviceRegistry == null) {
            logger.severe("ServiceRegistry não disponível para iniciar tarefa de tempo online!");
            return;
        }
        StatisticsService statisticsService = serviceRegistry.getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não foi encontrado no registo!"));

        TaskScheduler.runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            if (loginTimestamps.isEmpty()) {
                return;
            }
            loginTimestamps.forEach((uuid, loginTime) -> {
                try {
                    long sessionDuration = now - loginTime;
                    if (sessionDuration > 0) {
                        statisticsService.addOnlineTime(uuid, sessionDuration);
                        loginTimestamps.put(uuid, now);
                    } else if (sessionDuration < 0) {
                        logger.warning("Detectado tempo de sessão negativo para " + uuid + ". Resetando timestamp.");
                        loginTimestamps.put(uuid, now);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Erro ao salvar tempo online para " + uuid, e);
                }
            });
            if (!loginTimestamps.isEmpty()) {
                logger.info("Tempo online salvo periodicamente para " + loginTimestamps.size() + " jogadores.");
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
}