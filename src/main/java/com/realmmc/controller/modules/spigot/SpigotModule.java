package com.realmmc.controller.modules.spigot;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.services.SessionService;
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.role.RoleKickHandler;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.spigot.cash.SpigotCashCache;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import com.realmmc.controller.spigot.permission.SpigotPermissionInjector;
import com.realmmc.controller.spigot.permission.SpigotPermissionRefresher;
import com.realmmc.controller.spigot.sounds.SpigotSoundPlayer;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.shared.utils.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.HandlerList;

import com.viaversion.viaversion.api.Via;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotModule extends AbstractCoreModule {
    private final Plugin plugin;
    private SessionService sessionServiceInstance;
    private SpigotPermissionInjector permissionInjectorInstance;
    private Optional<SessionTrackerService> sessionTrackerServiceOpt;
    private ScheduledFuture<?> heartbeatTaskFuture = null;
    private final boolean viaVersionApiAvailable;

    private SpigotCashCache spigotCashCacheInstance;

    public SpigotModule(Plugin plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
    }

    @Override public String getName() { return "SpigotModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo específico para funcionalidades Spigot (v2)."; }

    @Override public int getPriority() { return 50; }

    @Override public String[] getDependencies() {
        return new String[]{
                "Profile", "RoleModule", "SchedulerModule", "Command", "Preferences", "Particle"
        };
    }

    @Override
    protected void onEnable() {
        logger.info("[SpigotModule] Registering Spigot-specific services and listeners (v2)...");
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        if(sessionTrackerServiceOpt.isEmpty()){
            logger.warning("[SpigotModule] SessionTrackerService not found! Heartbeat will not function.");
        }

        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new SpigotSoundPlayer());
            logger.info("[SpigotModule] SpigotSoundPlayer registered.");
        } catch (Exception e) { logger.log(Level.WARNING, "[SpigotModule] Failed to register SpigotSoundPlayer", e); }

        try { CommandManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "[SpigotModule] Failed to register Spigot commands!", e); }
        try { ListenersManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "[SpigotModule] Failed to register Spigot listeners!", e); }

        try {
            this.sessionServiceInstance = new SessionService(logger);
            Bukkit.getServer().getPluginManager().registerEvents(sessionServiceInstance, plugin);
            logger.info("[SpigotModule] SessionService (Listener) registered.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SpigotModule] Failed to register SessionService!", e);
            this.sessionServiceInstance = null;
        }

        if (this.sessionServiceInstance != null) {
            try {
                this.permissionInjectorInstance = new SpigotPermissionInjector(plugin, logger);
                Bukkit.getServer().getPluginManager().registerEvents(permissionInjectorInstance, plugin);
                logger.info("[SpigotModule] SpigotPermissionInjector (Listener) registered.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[SpigotModule] Failed to register SpigotPermissionInjector!", e);
                this.permissionInjectorInstance = null;
            }
        } else {
            logger.severe("[SpigotModule] SpigotPermissionInjector not registered because SessionService failed to initialize.");
        }

        try {
            this.spigotCashCacheInstance = new SpigotCashCache();
            ServiceRegistry.getInstance().registerService(SpigotCashCache.class, this.spigotCashCacheInstance);
            Bukkit.getServer().getPluginManager().registerEvents(this.spigotCashCacheInstance, plugin);

            RedisSubscriber redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            redisSubscriber.registerListener(RedisChannel.PROFILES_SYNC, this.spigotCashCacheInstance);

            logger.info("[SpigotModule] SpigotCashCache (para cache de cash e PAPI) registrado e ouvindo Redis.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SpigotModule] Falha ao registrar SpigotCashCache!", e);
            this.spigotCashCacheInstance = null;
        }

        ServiceRegistry.getInstance().getService(RoleService.class).ifPresentOrElse(roleService -> {
            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher spigotRefresher = new SpigotPermissionRefresher(plugin, logger);
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, spigotRefresher);
                    logger.info("[SpigotModule] SpigotPermissionRefresher registered in ServiceRegistry.");
                } catch (Exception e) { logger.log(Level.SEVERE, "[SpigotModule] Failed to register SpigotPermissionRefresher!", e); }

                try {
                    RoleKickHandler.PlatformKicker kicker = (uuid, formattedKickMessage) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                player.kickPlayer(formattedKickMessage);
                            }
                        });
                    };
                    RoleKickHandler.initialize(kicker);
                    logger.info("[SpigotModule] RoleKickHandler initialized for Spigot platform.");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[SpigotModule] Failed to initialize RoleKickHandler in SpigotModule!", e);
                }

            } else {
                logger.severe("[SpigotModule] SpigotPermissionRefresher (and KickHandler) not registered because SpigotPermissionInjector failed.");
            }
        }, () -> {
            logger.severe("[SpigotModule] RoleService NOT detected. Spigot permission integration will not be configured.");
        });

        startHeartbeatTask();

        sendServerReadySignal();

        logger.info("[SpigotModule] SpigotModule enabled successfully.");
    }

    private void sendServerReadySignal() {
        String serverName = System.getProperty("controller.serverId");
        if (serverName == null || serverName.isEmpty()) {
            serverName = System.getenv("CONTROLLER_SERVER_ID");
        }

        if (serverName != null && !serverName.isEmpty()) {
            try {
                String json = String.format("{\"server\":\"%s\",\"status\":\"ONLINE\"}", serverName);

                RedisPublisher.publish(
                        RedisChannel.SERVER_STATUS_UPDATE,
                        json
                );
                logger.info("[SpigotModule] Sinal de 'SERVER READY' enviado para o Redis (" + serverName + ").");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Falha ao enviar sinal de servidor pronto.", e);
            }
        }
    }

    @Override
    protected void onDisable() {
        logger.info("[SpigotModule] Disabling SpigotModule...");

        stopHeartbeatTask();

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        ServiceRegistry.getInstance().unregisterService(SpigotCashCache.class);
        logger.info("[SpigotModule] Spigot services unregistered.");

        if (sessionServiceInstance != null) {
            try { HandlerList.unregisterAll(sessionServiceInstance); }
            catch (Exception e) { logger.log(Level.WARNING, "[SpigotModule] Error unregistering SessionService.", e); }
            sessionServiceInstance = null;
        }
        if (permissionInjectorInstance != null) {
            try {
                HandlerList.unregisterAll(permissionInjectorInstance);
                permissionInjectorInstance.cleanupOnDisable();
            }
            catch (Exception e) { logger.log(Level.WARNING, "[SpigotModule] Error unregistering SpigotPermissionInjector.", e); }
            permissionInjectorInstance = null;
        }

        if (spigotCashCacheInstance != null) {
            try {
                HandlerList.unregisterAll(spigotCashCacheInstance);
                ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                        .ifPresent(sub -> sub.unregisterListener(RedisChannel.PROFILES_SYNC));
            } catch (Exception e) {
                logger.log(Level.WARNING, "[SpigotModule] Error unregistering SpigotCashCache.", e);
            }
            spigotCashCacheInstance = null;
        }

        try { HandlerList.unregisterAll(plugin); logger.info("[SpigotModule] Other Spigot listeners unregistered."); }
        catch (Exception e) { logger.log(Level.WARNING, "[SpigotModule] Error unregistering other plugin listeners.", e); }

        logger.info("[SpigotModule] SpigotModule disabled.");
    }

    private void startHeartbeatTask() {
        if (heartbeatTaskFuture != null && !heartbeatTaskFuture.isDone()) {
            logger.fine("[SpigotModule] Heartbeat task is already running.");
            return;
        }

        sessionTrackerServiceOpt.ifPresentOrElse(sessionTracker -> {
            try {
                heartbeatTaskFuture = TaskScheduler.runAsyncTimer(() -> {
                    try {
                        runHeartbeat(sessionTracker);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "[SpigotModule] Error in periodic Heartbeat Task (Spigot)", e);
                    }
                }, 10, 15, TimeUnit.SECONDS);
                logger.info("[SpigotModule] Heartbeat Task (Spigot) started.");
            } catch (IllegalStateException e) {
                logger.log(Level.SEVERE, "[SpigotModule] Failed to schedule Heartbeat Task (Spigot): TaskScheduler not initialized?", e);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[SpigotModule] Unexpected error scheduling Heartbeat Task (Spigot)", e);
            }
        }, () -> {
            logger.warning("[SpigotModule] Heartbeat Task (Spigot) not started: SessionTrackerService not found.");
        });
    }

    private void stopHeartbeatTask() {
        if (heartbeatTaskFuture != null) {
            try {
                heartbeatTaskFuture.cancel(false);
                logger.info("[SpigotModule] Heartbeat Task (Spigot) stopped.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[SpigotModule] Error stopping Heartbeat Task (Spigot)", e);
            } finally {
                heartbeatTaskFuture = null;
            }
        }
    }

    private void runHeartbeat(SessionTrackerService sessionTracker) {

        String serverName = System.getProperty("controller.serverId");
        if (serverName == null || serverName.isEmpty()) {
            serverName = System.getenv("CONTROLLER_SERVER_ID");
        }
        if (serverName == null || serverName.isEmpty()) {
            serverName = Bukkit.getServer().getName();
            logger.warning("Aviso: 'controller.serverId' (propriedade Java) ou 'CONTROLLER_SERVER_ID' (variável de ambiente) não estão definidas! O Heartbeat está a reportar o nome do servidor como: " + serverName);
        }

        final String finalServerName = serverName;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            UUID uuid = player.getUniqueId();
            int ping = player.getPing();
            int protocol = -1;

            try {
                if(viaVersionApiAvailable) {
                    protocol = Via.getAPI().getPlayerVersion(uuid);
                } else {
                    try {
                        Class<?> protocolSupportApi = Class.forName("protocolsupport.api.ProtocolSupportAPI");
                        Object apiInstance = protocolSupportApi.getMethod("getAPI").invoke(null);
                        protocol = (int) apiInstance.getClass().getMethod("getProtocolVersion", Player.class).invoke(apiInstance, player);
                    } catch (Exception | NoClassDefFoundError ignored) {
                        try {
                            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                            Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
                            Object networkManager = connection.getClass().getField("networkManager").get(connection);
                            try {
                                protocol = (int) networkManager.getClass().getMethod("getVersion").invoke(networkManager);
                            } catch (NoSuchMethodException ex) {
                                protocol = (int) networkManager.getClass().getMethod("getProtocolVersion").invoke(networkManager);
                            }
                        } catch (Exception | NoClassDefFoundError nmsEx) { protocol = -1;}
                    }
                }
            } catch (Exception | NoClassDefFoundError ignored) { protocol = -1; }


            try {
                sessionTracker.updateHeartbeat(uuid, finalServerName, ping, protocol);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[SpigotModule] Error sending heartbeat for " + player.getName() + " (UUID: " + uuid + ")", e);
            }
        }
    }
}