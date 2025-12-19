package com.palacesky.controller.modules.role;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.role.RoleKickHandler;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;

import org.bukkit.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class RoleModule extends AbstractCoreModule {

    private RoleService roleService;
    private RedisRoleSyncListener roleSyncListener;
    private RedisRoleUpdateListener roleUpdateListener;
    private RedisMessageListener roleBroadcastListener;

    public RoleModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "RoleModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo de gerenciamento de grupos e permissões (v2)."; }

    @Override public String[] getDependencies() { return new String[]{"Database", "Profile", "SchedulerModule"}; }

    @Override public int getPriority() { return 25; }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[RoleModule] Initializing RoleService and related components (v2)...");

        try {
            this.roleService = new RoleService(logger);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleModule] Critical failure instantiating RoleService! Dependencies not resolved?", e);
            throw e;
        }

        try {
            roleService.setupDefaultRoles();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleModule] SEVERE error during initial synchronization of default roles with MongoDB!", e);
            throw new RuntimeException("Failed to synchronize default roles with MongoDB.", e);
        }

        try {
            ServiceRegistry.getInstance().registerService(RoleService.class, roleService);
            logger.info("[RoleModule] RoleService registered successfully.");
        } catch (Exception e){
            logger.log(Level.SEVERE, "[RoleModule] Unexpected failure registering RoleService!", e);
            throw e;
        }


        RedisSubscriber redisSubscriber = null;
        try {
            redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);

            this.roleSyncListener = new RedisRoleSyncListener();
            roleSyncListener.startListening();
            logger.info("[RoleModule] RedisRoleSyncListener started and listening on ROLE_SYNC channel.");

            this.roleUpdateListener = new RedisRoleUpdateListener(this.roleService);
            roleUpdateListener.startListening(redisSubscriber);
            logger.info("[RoleModule] RedisRoleUpdateListener started and listening on ROLES_UPDATE channel (Global Role Cache Sync).");

        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[RoleModule] Failed to obtain RedisSubscriber necessary for RoleModule! Role synchronization will not work.", e);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[RoleModule] Failed to instantiate RedisRoleSyncListener (missing dependency?)! Role synchronization will not work.", e);
            this.roleSyncListener = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleModule] Unexpected failure starting RedisRoleSyncListener!", e);
            this.roleSyncListener = null;
        }

        if (redisSubscriber != null) {
            try {
                this.roleBroadcastListener = createBroadcastListener();
                if (this.roleBroadcastListener != null) {
                    redisSubscriber.registerListener(RedisChannel.ROLE_BROADCAST, this.roleBroadcastListener);
                    logger.info("[RoleModule] RoleBroadcastListener registered on ROLE_BROADCAST channel.");
                } else {
                    logger.warning("[RoleModule] RoleBroadcastListener not created on onEnable (Platform not yet detected?).");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[RoleModule] Failed to register RoleBroadcastListener!", e);
                this.roleBroadcastListener = null;
            }
        } else {
            logger.severe("[RoleModule] RedisSubscriber not found, RoleBroadcastListener not registered.");
        }

        logger.info("[RoleModule] RoleModule (v2) enabled.");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("[RoleModule] Disabling RoleModule (v2)...");

        if (roleSyncListener != null) {
            try { roleSyncListener.stopListening(); }
            catch (Exception e) { logger.log(Level.WARNING, "[RoleModule] Error calling stopListening() on RedisRoleSyncListener.", e); }
        }
        if (roleUpdateListener != null) {
            try { roleUpdateListener.stopListening(); }
            catch (Exception e) { logger.log(Level.WARNING, "[RoleModule] Error calling stopListening() on RedisRoleUpdateListener.", e); }
        }


        if (roleBroadcastListener != null) {
            try {
                ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                        .ifPresent(sub -> {
                            try {
                                sub.unregisterListener(RedisChannel.ROLE_BROADCAST);
                                logger.info("[RoleModule] RoleBroadcastListener unregistered.");
                            } catch (Exception ex) {
                                logger.log(Level.WARNING, "[RoleModule] Error unregistering RoleBroadcastListener.", ex);
                            }
                        });
            } catch (Exception e) {
                logger.log(Level.WARNING, "[RoleModule] Error obtaining RedisSubscriber to unregister RoleBroadcastListener.", e);
            }
        }

        try {
            RoleKickHandler.shutdown();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[RoleModule] Error calling shutdown() on RoleKickHandler.", e);
        }

        try {
            ServiceRegistry.getInstance().unregisterService(RoleService.class);
            logger.info("[RoleModule] RoleService unregistered.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "[RoleModule] Error unregistering RoleService.", e);
        }

        if (roleService != null) {
            try {
                roleService.shutdown();
            } catch (Exception e) {
                logger.log(Level.WARNING, "[RoleModule] Error calling shutdown() on RoleService.", e);
            }
        }

        this.roleService = null;
        this.roleSyncListener = null;
        this.roleBroadcastListener = null;
        this.roleUpdateListener = null;

        logger.info("[RoleModule] RoleModule (v2) finalized.");
    }

    private RedisMessageListener createBroadcastListener() {
        ServiceRegistry registry = ServiceRegistry.getInstance();
        if (registry.hasService(ProxyServer.class)) {
            logger.fine("[RoleModule] Velocity detected. Creating RoleBroadcastListener for Velocity.");
            return new com.palacesky.controller.proxy.listeners.RoleBroadcastListener();
        }
        else if (registry.hasService(Plugin.class)) {
            logger.fine("[RoleModule] Spigot detected. Creating RoleBroadcastListener for Spigot.");
            return new com.palacesky.controller.spigot.listeners.RoleBroadcastListener();
        }
        return null;
    }
}