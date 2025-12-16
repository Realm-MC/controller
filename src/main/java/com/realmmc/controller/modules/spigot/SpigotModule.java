package com.realmmc.controller.modules.spigot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.realmmc.controller.spigot.listeners.RoleBroadcastListener;
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
    private ScheduledFuture<?> playerHeartbeatTask = null;
    private ScheduledFuture<?> serverHeartbeatTask = null;
    private final boolean viaVersionApiAvailable;
    private RoleBroadcastListener roleBroadcastListener;
    private SpigotCashCache spigotCashCacheInstance;
    private final ObjectMapper mapper = new ObjectMapper();

    public SpigotModule(Plugin plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
    }

    @Override public String getName() { return "SpigotModule"; }
    @Override public String getVersion() { return "1.1.0"; }
    @Override public String getDescription() { return "Módulo Spigot (v2) com GameState Heartbeat."; }
    @Override public int getPriority() { return 50; }

    @Override public String[] getDependencies() {
        return new String[]{ "Profile", "RoleModule", "SchedulerModule", "Command", "Preferences", "Particle", "Statistics" };
    }

    @Override
    protected void onEnable() {
        logger.info("[SpigotModule] Registering Spigot-specific services and listeners...");
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);

        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new SpigotSoundPlayer());
        } catch (Exception e) { logger.log(Level.WARNING, "Failed to register SoundPlayer", e); }

        try { CommandManager.registerAll(plugin); } catch (Exception e) { logger.severe("Failed to register commands: " + e.getMessage()); }
        try { ListenersManager.registerAll(plugin); } catch (Exception e) { logger.severe("Failed to register listeners: " + e.getMessage()); }

        try {
            this.sessionServiceInstance = new SessionService(logger);
            Bukkit.getServer().getPluginManager().registerEvents(sessionServiceInstance, plugin);
        } catch (Exception e) {
            logger.severe("Failed to register SessionService!");
        }

        if (this.sessionServiceInstance != null) {
            try {
                this.permissionInjectorInstance = new SpigotPermissionInjector(plugin, logger);
                Bukkit.getServer().getPluginManager().registerEvents(permissionInjectorInstance, plugin);
            } catch (Exception e) {
                logger.severe("Failed to register PermissionInjector!");
            }
        }

        setupRedisListeners();
        setupRoleIntegration();

        startPlayerHeartbeatTask();
        startServerHeartbeatTask();

        sendServerHeartbeat(true);

        logger.info("[SpigotModule] Enabled successfully.");
    }

    private void setupRedisListeners() {
        try {
            this.spigotCashCacheInstance = new SpigotCashCache();
            ServiceRegistry.getInstance().registerService(SpigotCashCache.class, this.spigotCashCacheInstance);
            Bukkit.getServer().getPluginManager().registerEvents(this.spigotCashCacheInstance, plugin);

            RedisSubscriber sub = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            sub.registerListener(RedisChannel.PROFILES_SYNC, this.spigotCashCacheInstance);

            this.roleBroadcastListener = new RoleBroadcastListener();
            sub.registerListener(RedisChannel.ROLE_BROADCAST, this.roleBroadcastListener);
        } catch (Exception e) {
            logger.severe("Failed to setup Redis listeners: " + e.getMessage());
        }
    }

    private void setupRoleIntegration() {
        ServiceRegistry.getInstance().getService(RoleService.class).ifPresentOrElse(roleService -> {
            if (this.permissionInjectorInstance != null) {
                ServiceRegistry.getInstance().registerService(PermissionRefresher.class, new SpigotPermissionRefresher(plugin, logger));
                RoleKickHandler.initialize((uuid, msg) -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) p.kickPlayer(msg);
                    });
                });
            }
        }, () -> logger.severe("RoleService missing!"));
    }

    @Override
    protected void onDisable() {
        stopPlayerHeartbeatTask();
        stopServerHeartbeatTask();

        sendServerStatusJson("STOPPING");

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        ServiceRegistry.getInstance().unregisterService(SpigotCashCache.class);

        if (permissionInjectorInstance != null) {
            HandlerList.unregisterAll(permissionInjectorInstance);
            permissionInjectorInstance.cleanupOnDisable();
        }
        if (sessionServiceInstance != null) HandlerList.unregisterAll(sessionServiceInstance);

        RedisSubscriber sub = ServiceRegistry.getInstance().getService(RedisSubscriber.class).orElse(null);
        if (sub != null) {
            if (spigotCashCacheInstance != null) sub.unregisterListener(RedisChannel.PROFILES_SYNC);
            if (roleBroadcastListener != null) sub.unregisterListener(RedisChannel.ROLE_BROADCAST);
        }
    }

    private void startPlayerHeartbeatTask() {
        if (sessionTrackerServiceOpt.isPresent()) {
            playerHeartbeatTask = TaskScheduler.runAsyncTimer(() -> runPlayerHeartbeat(sessionTrackerServiceOpt.get()), 10, 15, TimeUnit.SECONDS);
        }
    }

    private void stopPlayerHeartbeatTask() {
        if (playerHeartbeatTask != null) {
            playerHeartbeatTask.cancel(false);
            playerHeartbeatTask = null;
        }
    }

    private void runPlayerHeartbeat(SessionTrackerService sessionTracker) {
        String serverName = getServerId();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            int protocol = -1;
            try { if(viaVersionApiAvailable) protocol = Via.getAPI().getPlayerVersion(uuid); } catch (Exception ignored) {}
            try {
                sessionTracker.updateHeartbeat(uuid, serverName, player.getPing(), protocol);
            } catch (Exception ignored) {}
        }
    }

    private void startServerHeartbeatTask() {
        serverHeartbeatTask = TaskScheduler.runAsyncTimer(() -> sendServerHeartbeat(false), 5, 5, TimeUnit.SECONDS);
    }

    private void stopServerHeartbeatTask() {
        if (serverHeartbeatTask != null) {
            serverHeartbeatTask.cancel(false);
            serverHeartbeatTask = null;
        }
    }

    private void sendServerHeartbeat(boolean forceOnline) {
        String serverId = getServerId();
        if (serverId == null || serverId.isEmpty()) return;

        String gameState = System.getProperty("game.state", "WAITING");
        String mapName = System.getProperty("game.map", "Unknown");
        boolean canShutdown = Boolean.parseBoolean(System.getProperty("server.canShutdown", "true"));

        String status = forceOnline ? "ONLINE" : "ONLINE";

        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("server", serverId);
            node.put("status", status);
            node.put("gameState", gameState);
            node.put("mapName", mapName);
            node.put("canShutdown", canShutdown);
            node.put("players", Bukkit.getOnlinePlayers().size());
            node.put("maxPlayers", Bukkit.getMaxPlayers());

            RedisPublisher.publish(RedisChannel.SERVER_STATUS_UPDATE, node.toString());
        } catch (Exception e) {
            logger.warning("Falha ao enviar Server Heartbeat.");
        }
    }

    private void sendServerStatusJson(String status) {
        String serverId = getServerId();
        if (serverId == null) return;
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("server", serverId);
            node.put("status", status);
            RedisPublisher.publish(RedisChannel.SERVER_STATUS_UPDATE, node.toString());
        } catch (Exception ignored) {}
    }

    private String getServerId() {
        String id = System.getProperty("controller.serverId");
        if (id == null || id.isEmpty()) id = System.getenv("CONTROLLER_SERVER_ID");
        if (id == null || id.isEmpty()) id = Bukkit.getServer().getName();
        return id;
    }
}