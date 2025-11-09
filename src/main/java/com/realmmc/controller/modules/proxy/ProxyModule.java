package com.realmmc.controller.modules.proxy;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
import com.realmmc.controller.proxy.permission.VelocityPermissionInjector;
import com.realmmc.controller.proxy.permission.VelocityPermissionRefresher;
import com.realmmc.controller.proxy.sounds.VelocitySoundPlayer;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.role.RoleKickHandler;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.proxy.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProxyModule extends AbstractCoreModule {
    private final ProxyServer server;
    private final Object pluginInstance;
    private VelocityPermissionInjector permissionInjectorInstance;
    private Optional<SessionTrackerService> sessionTrackerServiceOpt;
    private ScheduledFuture<?> heartbeatTaskFuture = null;
    private ScheduledFuture<?> reaperTaskFuture = null;
    private static final String SESSION_PREFIX = "controller:session:";

    public ProxyModule(ProxyServer server, Object pluginInstance, Logger logger) {
        super(logger);
        this.server = server;
        this.pluginInstance = pluginInstance;
    }

    @Override public String getName() { return "ProxyModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo específico para funcionalidades Velocity (v2)."; }
    @Override public int getPriority() { return 50; }
    @Override public String[] getDependencies() {
        return new String[]{
                "Profile",
                "RoleModule",
                "SchedulerModule",
                "Command",
                "Preferences",
                "ServerManager"
        };
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando serviços e listeners específicos do Velocity (v2)...");
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("SessionTrackerService não encontrado no ProxyModule! Heartbeat e Reaper não funcionarão.");
        }

        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new VelocitySoundPlayer());
            logger.info("VelocitySoundPlayer registrado.");
        } catch (Exception e) { logger.log(Level.WARNING, "Falha ao registrar VelocitySoundPlayer", e); }

        try { CommandManager.registerAll(pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar comandos Velocity!", e); }

        try { ListenersManager.registerAll(server, pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar listeners Velocity!", e); }

        Optional<RoleService> roleServiceOpt = ServiceRegistry.getInstance().getService(RoleService.class);
        if (roleServiceOpt.isPresent()) {
            logger.info("RoleService detectado. Configurando integração de permissões Velocity...");

            try {
                this.permissionInjectorInstance = new VelocityPermissionInjector(pluginInstance, logger);
                server.getEventManager().register(pluginInstance, permissionInjectorInstance);
                logger.info("VelocityPermissionInjector registrado como listener.");
            } catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar VelocityPermissionInjector", e); this.permissionInjectorInstance = null; }

            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher velocityRefresher = new VelocityPermissionRefresher(server, pluginInstance, logger);
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, velocityRefresher);
                    logger.info("VelocityPermissionRefresher registrado no ServiceRegistry.");
                } catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar VelocityPermissionRefresher!", e); }
            } else { logger.severe("VelocityPermissionRefresher não registrado porque o Injetor falhou."); }

            try {
                RoleKickHandler.PlatformKicker kicker = (uuid, formattedKickMessage) -> {
                    server.getPlayer(uuid).ifPresent(player -> {
                        net.kyori.adventure.text.Component kickComponent = MiniMessage.miniMessage().deserialize(formattedKickMessage);
                        player.disconnect(kickComponent);
                    });
                };
                RoleKickHandler.initialize(kicker);
                logger.info("RoleKickHandler inicializado para plataforma Velocity.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao inicializar RoleKickHandler no ProxyModule!", e);
            }

        } else {
            logger.severe("RoleService NÃO detectado (pode ter falhado ao carregar). Integração de permissões Velocity não será configurada.");
        }

        startHeartbeatTask();
        startReaperTask();

        logger.info("ProxyModule habilitado.");
    }

    @Override
    protected void onDisable() {
        logger.info("Desabilitando ProxyModule...");
        stopHeartbeatTask();
        stopReaperTask();

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        logger.info("Serviços Velocity desregistrados.");

        if (permissionInjectorInstance != null) {
            try { server.getEventManager().unregisterListener(pluginInstance, permissionInjectorInstance); }
            catch (Exception e) { logger.log(Level.WARNING, "Erro ao desregistrar VelocityPermissionInjector.", e); }
            permissionInjectorInstance = null;
        }
        try { server.getEventManager().unregisterListeners(pluginInstance); }
        catch (Exception e) { logger.log(Level.WARNING, "Erro ao desregistrar outros listeners Velocity.", e); }
        logger.info("Listeners Velocity desregistrados.");

        logger.info("ProxyModule desabilitado.");
    }


    // --- Métodos para Heartbeat Task (Velocity) ---
    private void startHeartbeatTask() {
        if (heartbeatTaskFuture != null && !heartbeatTaskFuture.isDone()) {
            return;
        }
        sessionTrackerServiceOpt.ifPresentOrElse(sessionTracker -> {
            try {
                heartbeatTaskFuture = TaskScheduler.runAsyncTimer(() -> {
                    try {
                        runHeartbeat(sessionTracker);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro na execução periódica do Heartbeat Task (Velocity)", e);
                    }
                }, 10, 15, TimeUnit.SECONDS);
                logger.info("Tarefa de Heartbeat (Velocity) iniciada.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro inesperado ao agendar Heartbeat Task (Velocity)", e);
            }
        }, () -> {
            logger.warning("Heartbeat Task (Velocity) não iniciada: SessionTrackerService não encontrado.");
        });
    }

    private void stopHeartbeatTask() {
        if (heartbeatTaskFuture != null) {
            try {
                heartbeatTaskFuture.cancel(false);
                logger.info("Tarefa de Heartbeat (Velocity) parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar Heartbeat Task (Velocity)", e);
            } finally {
                heartbeatTaskFuture = null;
            }
        }
    }

    private void runHeartbeat(SessionTrackerService sessionTracker) {
        try (Jedis jedis = RedisManager.getResource()) {
            String playerCountStr = String.valueOf(server.getPlayerCount());
            jedis.setex(RedisChannel.GLOBAL_PLAYER_COUNT.getName(), 20, playerCountStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Falha ao salvar contagem global de jogadores no Redis.", e);
        }

        for (Player player : server.getAllPlayers()) {
            if (!player.isActive()) continue;
            UUID uuid = player.getUniqueId();
            String currentServerName = player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse(null);
            int ping = (int) player.getPing();
            int protocol = player.getProtocolVersion().getProtocol();
            try {
                sessionTracker.updateHeartbeat(uuid, currentServerName, ping, protocol);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao enviar heartbeat para " + player.getUsername() + " (UUID: " + uuid + ")", e);
            }
        }
    }
    // --- Fim Métodos Heartbeat ---


    // --- Métodos para Reaper Task (SOMENTE NO PROXY) ---
    private void startReaperTask() {
        // <<< CORREÇÃO: Mudar de getenv para getProperty >>>
        String runReaperEnv = System.getProperty("RUN_SESSION_REAPER"); // Era getenv
        // <<< FIM CORREÇÃO >>>
        boolean shouldRunReaper = "true".equalsIgnoreCase(runReaperEnv);

        if (!shouldRunReaper) {
            logger.info("Tarefa Reaper desativada nesta instância (Flag -DRUN_SESSION_REAPER != true).");
            return;
        }

        if (reaperTaskFuture != null && !reaperTaskFuture.isDone()) {
            return;
        }

        sessionTrackerServiceOpt.ifPresentOrElse(sessionTracker -> {
            try {
                reaperTaskFuture = TaskScheduler.runAsyncTimer(() -> {
                    try {
                        runReaper(sessionTracker);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro na execução periódica do Reaper Task", e);
                    }
                }, 30, 60, TimeUnit.SECONDS);
                logger.info("Tarefa Reaper (Limpador de Sessões Inativas) iniciada.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro inesperado ao agendar Reaper Task", e);
            }
        }, () -> {
            logger.warning("Reaper Task não iniciada: SessionTrackerService não encontrado.");
        });
    }

    private void stopReaperTask() {
        if (reaperTaskFuture != null) {
            try {
                reaperTaskFuture.cancel(false);
                logger.info("Tarefa Reaper parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar Reaper Task", e);
            } finally {
                reaperTaskFuture = null;
            }
        }
    }

    private void runReaper(SessionTrackerService sessionTracker) {
        logger.fine("Executando Reaper Task para limpar sessões inativas...");
        long now = System.currentTimeMillis();
        long heartbeatIntervalMillis = 15 * 1000;
        long thresholdMillis = (3 * heartbeatIntervalMillis) + 10000;
        int cleanedCount = 0;

        Set<String> onlineUsernames = sessionTracker.getOnlineUsernames();
        if (onlineUsernames.isEmpty()) {
            return;
        }

        for (String username : onlineUsernames) {
            Optional<UUID> uuidOpt = getUuidFromUsername(username);
            if (uuidOpt.isEmpty()) {
                logger.log(Level.FINER, "Reaper: UUID não encontrado para {0}, pulando verificação de heartbeat.", username);
                continue;
            }
            UUID uuid = uuidOpt.get();
            String sessionKey = SESSION_PREFIX + uuid.toString();

            try (Jedis jedis = RedisManager.getResource()) {
                String lastHeartbeatStr = jedis.hget(sessionKey, "lastHeartbeat");

                if (lastHeartbeatStr != null) {
                    try {
                        long lastHeartbeat = Long.parseLong(lastHeartbeatStr);
                        if (now - lastHeartbeat > thresholdMillis) {
                            logger.log(Level.WARNING, "Reaper: Sessão inativa detectada para {0} (UUID: {1}). Último heartbeat: {2}ms atrás. Limpando...",
                                    new Object[]{username, uuid, now - lastHeartbeat});
                            sessionTracker.endSession(uuid, username);
                            cleanedCount++;
                        }
                    } catch (NumberFormatException e) {
                        logger.log(Level.WARNING, "Reaper: Timestamp inválido para {0} (UUID: {1}). Limpando.", new Object[]{username, uuid});
                        sessionTracker.endSession(uuid, username);
                        cleanedCount++;
                    }
                } else {
                    String state = jedis.hget(sessionKey, "state");
                    if (!"ONLINE".equals(state)) {
                        logger.log(Level.WARNING, "Reaper: Hash de sessão encontrado para {0} (UUID: {1}) mas sem timestamp ou em estado não ONLINE ({2}). Limpando.", new Object[]{username, uuid, state == null ? "N/A" : state});
                        sessionTracker.endSession(uuid, username);
                        cleanedCount++;
                    }
                }
            } catch (JedisConnectionException e) {
                logger.log(Level.SEVERE, "Reaper: Erro de conexão Redis durante a verificação. Abortando ciclo.", e);
                return;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Reaper: Erro inesperado ao processar {0} (UUID: {1})", new Object[]{username, uuid});
            }
        }

        if (cleanedCount > 0) {
            logger.log(Level.INFO, "Reaper Task concluída. {0} sessões inativas foram limpas.", cleanedCount);
        }
    }

    private Optional<UUID> getUuidFromUsername(String username) {
        Optional<ProfileService> profileServiceOpt = ServiceRegistry.getInstance().getService(ProfileService.class);
        if (profileServiceOpt.isEmpty()) {
            logger.log(Level.SEVERE, "Reaper: ProfileService não disponível para buscar UUID de {0}", username);
            return Optional.empty();
        }

        Optional<UUID> uuidFromService = profileServiceOpt
                .flatMap(ps -> ps.getByUsername(username.toLowerCase()))
                .map(Profile::getUuid);

        if(uuidFromService.isPresent()){
            return uuidFromService;
        }

        Optional<Player> playerOpt = server.getPlayer(username);
        if(playerOpt.isPresent()){
            return Optional.of(playerOpt.get().getUniqueId());
        }

        return Optional.empty();
    }
}