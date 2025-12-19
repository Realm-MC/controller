package com.palacesky.controller.proxy.permission;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.session.SessionTrackerService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VelocityPermissionInjector {

    private final RoleService roleService;
    private final Logger logger;
    private final VelocityPermissionProvider provider;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;

    public VelocityPermissionInjector(Object pluginInstance, Logger logger) {
        this.logger = logger;
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.provider = new VelocityPermissionProvider(logger);
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
    }

    @Subscribe
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        boolean loadSuccess = false;

        try {
            roleService.loadPlayerDataAsync(uuid).get(8, TimeUnit.SECONDS);

            event.setProvider(this.provider);
            loadSuccess = true;

            sessionTrackerServiceOpt.ifPresent(service ->
                    service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE)
            );
            logger.info("[VelocityPerm] " + playerName + " is now ONLINE.");

        } catch (TimeoutException e) {
            logger.warning("[VelocityPerm] Timeout ao carregar permissões para " + playerName + ". O banco de dados pode estar lento.");
            Component kick = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_TIMEOUT));
            player.disconnect(kick);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[VelocityPerm] Failed to load permissions for " + playerName, e);
            Component kick = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_ERROR));
            player.disconnect(kick);
        } finally {
            if (!loadSuccess) {
                sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, playerName));
                roleService.removePreLoginFuture(uuid);
            }
        }
    }
}