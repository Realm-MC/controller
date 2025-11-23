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
import java.util.logging.Level;
import java.util.logging.Logger;

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
    @Override public String getDescription() { return "Módulo específico para funcionalidades Velocity."; }
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
        logger.info("[ProxyModule] Registrando serviços e listeners específicos do Velocity...");
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("[ProxyModule] SessionTrackerService não encontrado. Heartbeat e Reaper não funcionarão.");
        }

        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new VelocitySoundPlayer());
            logger.info("[ProxyModule] VelocitySoundPlayer registrado.");
        } catch (Exception e) { logger.log(Level.WARNING, "[ProxyModule] Falha ao registrar VelocitySoundPlayer", e); }

        try { CommandManager.registerAll(pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "[ProxyModule] Falha ao registrar comandos Velocity.", e); }

        try { ListenersManager.registerAll(server, pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "[ProxyModule] Falha ao registrar listeners Velocity.", e); }

        Optional<RoleService> roleServiceOpt = ServiceRegistry.getInstance().getService(RoleService.class);
        if (roleServiceOpt.isPresent()) {
            logger.info("[ProxyModule] RoleService detectado. Configurando integração de permissões Velocity...");

            try {
                this.permissionInjectorInstance = new VelocityPermissionInjector(pluginInstance, logger);
                server.getEventManager().register(pluginInstance, permissionInjectorInstance);
                logger.info("[ProxyModule] VelocityPermissionInjector registrado como listener.");
            } catch (Exception e) { logger.log(Level.SEVERE, "[ProxyModule] Falha ao registrar VelocityPermissionInjector", e); this.permissionInjectorInstance = null; }

            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher velocityRefresher = new VelocityPermissionRefresher(server, pluginInstance, logger);
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, velocityRefresher);
                    logger.info("[ProxyModule] VelocityPermissionRefresher registrado no ServiceRegistry.");
                } catch (Exception e) { logger.log(Level.SEVERE, "[ProxyModule] Falha ao registrar VelocityPermissionRefresher.", e); }
            } else { logger.severe("[ProxyModule] VelocityPermissionRefresher não registrado porque o Injetor falhou."); }

            try {
                RoleKickHandler.PlatformKicker kicker = (uuid, formattedKickMessage) -> {
                    server.getPlayer(uuid).ifPresent(player -> {
                        net.kyori.adventure.text.Component kickComponent = MiniMessage.miniMessage().deserialize(formattedKickMessage);
                        player.disconnect(kickComponent);
                    });
                };
                RoleKickHandler.initialize(kicker);
                logger.info("[ProxyModule] RoleKickHandler inicializado para plataforma Velocity.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ProxyModule] Falha ao inicializar RoleKickHandler.", e);
            }

        } else {
            logger.severe("[ProxyModule] RoleService NÃO detectado. Integração de permissões Velocity não configurada.");
        }

        startHeartbeatTask();
        startReaperTask();

        logger.info("[ProxyModule] Módulo habilitado com sucesso.");
    }

    @Override
    protected void onDisable() {
        logger.info("[ProxyModule] Desabilitando módulo...");
        stopHeartbeatTask();
        stopReaperTask();

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        logger.info("[ProxyModule] Serviços Velocity desregistrados.");

        if (permissionInjectorInstance != null) {
            try { server.getEventManager().unregisterListener(pluginInstance, permissionInjectorInstance); }
            catch (Exception e) { logger.log(Level.WARNING, "[ProxyModule] Erro ao desregistrar VelocityPermissionInjector.", e); }
            permissionInjectorInstance = null;
        }
        try { server.getEventManager().unregisterListeners(pluginInstance); }
        catch (Exception e) { logger.log(Level.WARNING, "[ProxyModule] Erro ao desregistrar outros listeners Velocity.", e); }
        logger.info("[ProxyModule] Listeners Velocity desregistrados.");

        logger.info("[ProxyModule] Módulo finalizado.");
    }

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
                        logger.log(Level.SEVERE, "[ProxyModule] Erro na execução periódica do Heartbeat Task", e);
                    }
                }, 10, 15, TimeUnit.SECONDS);
                logger.info("[ProxyModule] Tarefa de Heartbeat iniciada.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ProxyModule] Erro inesperado ao agendar Heartbeat Task", e);
            }
        }, () -> {
            logger.warning("[ProxyModule] Heartbeat Task não iniciada: SessionTrackerService não encontrado.");
        });
    }

    private void stopHeartbeatTask() {
        if (heartbeatTaskFuture != null) {
            try {
                heartbeatTaskFuture.cancel(false);
                logger.info("[ProxyModule] Tarefa de Heartbeat parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ProxyModule] Erro ao parar Heartbeat Task", e);
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
            logger.log(Level.WARNING, "[ProxyModule] Falha ao salvar contagem global de jogadores no Redis.", e);
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
                logger.log(Level.WARNING, "[ProxyModule] Erro ao enviar heartbeat para " + player.getUsername(), e);
            }
        }
    }

    private void startReaperTask() {
        String runReaperEnv = System.getProperty("RUN_SESSION_REAPER");
        boolean shouldRunReaper = "true".equalsIgnoreCase(runReaperEnv);

        if (!shouldRunReaper) {
            logger.info("[ProxyModule] Tarefa Reaper desativada nesta instância.");
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
                        logger.log(Level.SEVERE, "[ProxyModule] Erro na execução periódica do Reaper Task", e);
                    }
                }, 30, 60, TimeUnit.SECONDS);
                logger.info("[ProxyModule] Tarefa Reaper iniciada.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ProxyModule] Erro inesperado ao agendar Reaper Task", e);
            }
        }, () -> {
            logger.warning("[ProxyModule] Reaper Task não iniciada: SessionTrackerService não encontrado.");
        });
    }

    private void stopReaperTask() {
        if (reaperTaskFuture != null) {
            try {
                reaperTaskFuture.cancel(false);
                logger.info("[ProxyModule] Tarefa Reaper parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ProxyModule] Erro ao parar Reaper Task", e);
            } finally {
                reaperTaskFuture = null;
            }
        }
    }

    private void runReaper(SessionTrackerService sessionTracker) {
        logger.fine("[ProxyModule] Executando Reaper Task...");
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
                            logger.log(Level.WARNING, "[ProxyModule] Sessão inativa detectada para {0}. Limpando...", username);
                            sessionTracker.endSession(uuid, username);
                            cleanedCount++;
                        }
                    } catch (NumberFormatException e) {
                        sessionTracker.endSession(uuid, username);
                        cleanedCount++;
                    }
                } else {
                    String state = jedis.hget(sessionKey, "state");
                    if (!"ONLINE".equals(state)) {
                        sessionTracker.endSession(uuid, username);
                        cleanedCount++;
                    }
                }
            } catch (JedisConnectionException e) {
                logger.log(Level.SEVERE, "[ProxyModule] Erro de conexão Redis no Reaper.", e);
                return;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ProxyModule] Erro inesperado no Reaper para " + username, e);
            }
        }

        if (cleanedCount > 0) {
            logger.log(Level.INFO, "[ProxyModule] Reaper Task concluída. {0} sessões limpas.", cleanedCount);
        }
    }

    private Optional<UUID> getUuidFromUsername(String username) {
        Optional<ProfileService> profileServiceOpt = ServiceRegistry.getInstance().getService(ProfileService.class);
        if (profileServiceOpt.isEmpty()) {
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