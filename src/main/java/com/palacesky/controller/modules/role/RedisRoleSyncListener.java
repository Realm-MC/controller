package com.palacesky.controller.modules.role;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.role.PermissionRefresher;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisRoleSyncListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RedisRoleSyncListener.class.getName());
    private final RedisSubscriber subscriber;
    private final RoleService roleService;

    public RedisRoleSyncListener() {
        this.subscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
    }

    public void startListening() {
        subscriber.registerListener(RedisChannel.ROLE_SYNC, this);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_SYNC.getName().equals(channel)) return;

        try {
            UUID uuid = UUID.fromString(message);

            roleService.invalidateSession(uuid);

            ServiceRegistry.getInstance().getService(PermissionRefresher.class).ifPresent(refresher -> {
                refresher.refreshPlayerPermissions(uuid);
                LOGGER.info("[RoleSync] Permissões recarregadas instantaneamente para " + uuid);
            });

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro no RoleSync: " + message, e);
        }
    }

    public void stopListening() {
        subscriber.unregisterListener(RedisChannel.ROLE_SYNC);
    }
}