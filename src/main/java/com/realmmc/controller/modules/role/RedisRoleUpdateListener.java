package com.realmmc.controller.modules.role;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisRoleUpdateListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RedisRoleUpdateListener.class.getName());
    private final RoleService roleService;

    public RedisRoleUpdateListener(RoleService roleService) {
        this.roleService = roleService;
        LOGGER.info("[RoleUpdateListener] Initialized.");
    }

    public void startListening(RedisSubscriber subscriber) {
        try {
            subscriber.registerListener(RedisChannel.ROLES_UPDATE, this);
            LOGGER.info("[RoleUpdateListener] Registered on Redis channel ROLES_UPDATE.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[RoleUpdateListener] Critical failure registering listener on RedisSubscriber for ROLES_UPDATE!", e);
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLES_UPDATE.getName().equals(channel)) {
            return;
        }

        try {
            LOGGER.info("[RoleUpdateListener] Received ROLES_UPDATE signal. Reloading all roles from MongoDB...");

            roleService.loadRolesToCache(false);

            LOGGER.info("[RoleUpdateListener] Role cache (roleCache) successfully reloaded.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[RoleUpdateListener] Unexpected error processing ROLES_UPDATE message: " + message, e);
        }
    }

    public void stopListening() {
    }
}