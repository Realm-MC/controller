package com.realmmc.controller.proxy;

// Core Controller imports
import com.google.inject.Inject;
import com.realmmc.controller.core.ControllerCore;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;

// Modules imports
import com.realmmc.controller.modules.proxy.ProxyModule; // Import ProxyModule
import com.realmmc.controller.modules.scheduler.SchedulerModule; // Import SchedulerModule

// Shared services imports
import com.realmmc.controller.shared.geoip.GeoIPService;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.shared.stats.StatisticsService; // Para salvar tempo online
import com.realmmc.controller.shared.storage.redis.RedisSubscriber; // Import RedisSubscriber
import com.realmmc.controller.shared.utils.TaskScheduler;

// Velocity imports
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer; // Import Velocity ProxyServer

// Lombok import
import lombok.Getter;

// Java IO and NIO imports
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Java Util imports
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Logging imports
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(id = "controller", name = "Controller", description = "Core controller para RealmMC (v2)", version = "1.0.0", authors = {"xxxlc"})
public class Proxy extends ControllerCore { // Herda de ControllerCore

    @Getter
    private static Proxy instance;
    @Getter
    private final ProxyServer server;
    private final Path dataDirectory; // Diretório de dados injetado

    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;
    private GeoIPService geoIPService;

    // Caches relacionados a login/sessão
    @Getter
    private final ConcurrentHashMap<UUID, Long> loginTimestamps = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, Boolean> premiumLoginStatus = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, UUID> offlineUuids = new ConcurrentHashMap<>();

    @Inject
    public Proxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        super(logger); // Passa logger para ControllerCore
        this.server = server;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    /**
     * Método de inicialização principal chamado pelo Velocity (via onEnable).
     */
    @Override
    public void initialize() {
        try {
            logger.info("Inicializando Controller Core (Proxy - v2)...");

            // --- Inicialização de Serviços Core ---
            serviceRegistry = new ServiceRegistry(logger);

            // <<< REGISTRAR PROXY SERVER >>>
            serviceRegistry.registerService(ProxyServer.class, this.server);
            logger.info("ProxyServer registrado no ServiceRegistry.");
            // <<< FIM REGISTRO >>>

            initializeSharedServices(); // Inicializa GeoIP, MessagingSDK, etc.

            // --- Inicialização do ModuleManager e Módulos ---
            moduleManager = new ModuleManager(logger);

            // Auto-registro de módulos gerais e PROXY
            moduleManager.autoRegisterModules(AutoRegister.Platform.PROXY, getClass());

            // Registro manual de módulos específicos da plataforma
            moduleManager.registerModule(new SchedulerModule(server, this, logger));
            // ProxyModule deve ser registrado aqui para que dependências como ProxyServer estejam disponíveis para RoleModule
            moduleManager.registerModule(new ProxyModule(server, this, logger));
            // RoleModule (e outros como Database, Profile) são carregados via autoRegister

            // Habilita todos os módulos registrados
            moduleManager.enableAllModules();

            // --- Tarefas Adicionais ---
            startOnlineTimeBackupTask(); // Inicia tarefa periódica

            logger.info("Controller Core (Proxy - v2) inicializado com sucesso!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro fatal durante a inicialização do Controller no Proxy!", e);
            // Considerar desabilitar o plugin ou impedir o carregamento completo
            // Por exemplo, lançando RuntimeException aqui faria o Velocity descarregar o plugin.
        }
    }

    /**
     * Inicializa serviços compartilhados como GeoIP e MessagingSDK.
     */
    @Override
    protected void initializeSharedServices() {
        super.initializeSharedServices(); // Chama implementação base (se houver)

        try {
            // Garante que o diretório de dados e de mensagens existam
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path messagesPath = dataDirectory.resolve("messages");
            if (Files.notExists(messagesPath)) {
                Files.createDirectories(messagesPath);
            }

            // Copia arquivos de mensagens padrão se não existirem
            copyResourceIfNotExists("messages/pt_BR.properties", messagesPath.resolve("pt_BR.properties"));
            copyResourceIfNotExists("messages/en.properties", messagesPath.resolve("en.properties"));

            // Inicializa e registra GeoIPService
            geoIPService = new GeoIPService(dataDirectory.toFile(), logger);
            if (serviceRegistry != null) {
                serviceRegistry.registerService(GeoIPService.class, geoIPService);
                logger.info("GeoIPService registrado.");
            } else {
                logger.severe("ServiceRegistry não inicializado antes de registrar GeoIPService!");
            }

            // Inicializa MessagingSDK para Velocity
            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForVelocity(messagesPath.toFile());
                logger.info("MessagingSDK inicializado para Velocity.");
            } else {
                logger.warning("Tentativa de reinicializar MessagingSDK ignorada.");
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Falha ao inicializar serviços compartilhados (mensagens/GeoIP) no Proxy!", e);
        }
    }

    /**
     * Copia um recurso interno para um caminho de destino se ele não existir.
     */
    private void copyResourceIfNotExists(String resourcePath, Path targetPath) throws IOException {
        if (Files.notExists(targetPath)) {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
                if (stream == null) {
                    logger.warning("Recurso não encontrado no JAR: /" + resourcePath);
                    return;
                }
                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.finer("Recurso padrão copiado: /" + resourcePath + " -> " + targetPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Falha ao copiar recurso padrão: /" + resourcePath, e);
                throw e;
            } catch (NullPointerException e) {
                logger.warning("NPE ao tentar obter recurso: /" + resourcePath + ". Verifique o caminho.");
            }
        }
    }

    /**
     * Método de finalização chamado pelo Velocity (via onDisable).
     */
    @Override
    public void shutdown() {
        try {
            logger.info("Finalizando Controller Core (Proxy - v2)...");

            // --- Salva dados pendentes (ex: tempo online) ---
            ServiceRegistry currentRegistry = ServiceRegistry.getInstance();
            if (currentRegistry != null) {
                currentRegistry.getService(StatisticsService.class).ifPresent(statsService -> {
                    logger.info("Salvando tempo online final...");
                    server.getAllPlayers().forEach(player -> {
                        Long loginTime = loginTimestamps.remove(player.getUniqueId());
                        if (loginTime != null) {
                            long sessionDuration = System.currentTimeMillis() - loginTime;
                            if (sessionDuration > 0) {
                                try {
                                    statsService.addOnlineTime(player.getUniqueId(), sessionDuration);
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Erro ao salvar tempo online para " + player.getUsername(), e);
                                }
                            }
                        }
                    });
                    logger.info("Tempo online final salvo.");
                });
            }

            // --- Desabilita Módulos na Ordem Inversa ---
            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

        } finally {
            // --- Finaliza Serviços Core ---
            if (geoIPService != null) {
                try { geoIPService.close(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao fechar GeoIPService.", e); }
            }
            MessagingSDK.getInstance().shutdown();

            // <<< DESREGISTRAR PROXY SERVER >>>
            ServiceRegistry currentRegistry = ServiceRegistry.getInstance(); // Pega instância de novo (pode ter sido limpa)
            if (currentRegistry != null) {
                try { currentRegistry.unregisterService(ProxyServer.class); } catch (Exception e) { /* Log */ }
                // Desregistra outros serviços que foram registrados aqui, se houver
                try { currentRegistry.unregisterService(GeoIPService.class); } catch (Exception e) { /* Log */ }
                logger.info("Serviços registrados por Proxy desregistrados.");
            }
            // <<< FIM DESREGISTRO >>>


            // Chama shutdown da classe base
            shutdownSharedServices();

            // Limpa referências
            serviceRegistry = null;
            moduleManager = null;
            geoIPService = null;
            instance = null;

            logger.info("Controller Core (Proxy - v2) finalizado.");
        }
    }

    /**
     * Listener do evento de inicialização do Velocity.
     */
    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        initialize();
    }

    /**
     * Listener do evento de desligamento do Velocity.
     */
    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        shutdown();
    }

    /**
     * Inicia a tarefa periódica para salvar o tempo online acumulado.
     */
    private void startOnlineTimeBackupTask() {
        ServiceRegistry currentRegistry = ServiceRegistry.getInstance();
        if (currentRegistry == null) {
            logger.severe("ServiceRegistry não disponível para iniciar tarefa de tempo online!");
            return;
        }
        Optional<StatisticsService> statsOpt = currentRegistry.getService(StatisticsService.class);
        if (statsOpt.isEmpty()) {
            logger.warning("StatisticsService não encontrado. Tarefa de backup de tempo online não iniciada.");
            return;
        }
        StatisticsService statisticsService = statsOpt.get();

        try {
            TaskScheduler.runAsyncTimer(() -> {
                long now = System.currentTimeMillis();
                if (loginTimestamps.isEmpty()) {
                    return; // Sai cedo
                }
                loginTimestamps.forEach((uuid, loginTime) -> {
                    try {
                        long sessionDurationSinceLastSave = now - loginTime;
                        if (sessionDurationSinceLastSave > 0) {
                            statisticsService.addOnlineTime(uuid, sessionDurationSinceLastSave);
                            loginTimestamps.put(uuid, now); // Atualiza timestamp
                        } else if (sessionDurationSinceLastSave < 0) {
                            logger.warning("Detectado tempo de sessão negativo para " + uuid + ". Resetando timestamp.");
                            loginTimestamps.put(uuid, now);
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro ao salvar tempo online periódico para " + uuid, e);
                    }
                });
                if (!loginTimestamps.isEmpty()) {
                    // logger.fine("Tempo online salvo periodicamente para " + loginTimestamps.size() + " jogadores.");
                }
            }, 5, 5, TimeUnit.MINUTES);
            logger.info("Tarefa de backup periódico de tempo online iniciada (a cada 5 minutos).");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao agendar tarefa de tempo online: TaskScheduler não inicializado?", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao agendar tarefa de tempo online.", e);
        }
    }

    // --- Sobrescreve shutdownSharedServices se precisar de lógica extra no Proxy ---
    // @Override
    // protected void shutdownSharedServices() {
    //     super.shutdownSharedServices();
    //     logger.info("Finalizando serviços compartilhados específicos do Proxy...");
    //     // Adicionar lógica de shutdown específica do Proxy aqui, se necessário
    // }
}