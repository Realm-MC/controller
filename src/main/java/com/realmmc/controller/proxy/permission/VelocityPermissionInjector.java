package com.realmmc.controller.proxy.permission;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VelocityPermissionInjector {

    private final RoleService roleService;
    private final Logger logger;
    private final VelocityPermissionProvider provider;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Object pluginInstance;
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;

    public VelocityPermissionInjector(Object pluginInstance, Logger logger) {
        this.pluginInstance = pluginInstance;
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            this.provider = new VelocityPermissionProvider(logger);
            this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
            if (sessionTrackerServiceOpt.isEmpty()) {
                logger.warning("[VelocityPerm] SessionTrackerService not found in injector!");
            }
            logger.info("[VelocityPerm] Permission injector prepared.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[VelocityPerm] Critical Error: Dependent service (RoleService or SessionTrackerService) not found during initialization!", e);
            throw new RuntimeException("Failed to initialize VelocityPermissionInjector: Missing dependency(ies).", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[VelocityPerm] Unexpected error creating injector or provider!", e);
            throw new RuntimeException("Failed to initialize VelocityPermissionInjector.", e);
        }
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getUniqueId(), event.getUsername()));
        }
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.NORMAL)
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        logger.finer("[VelocityPerm] PermissionsSetupEvent for " + playerName + ". Initiating synchronous load, injecting provider, and setting ONLINE state...");

        boolean loadSuccess = false;
        try {
            roleService.loadPlayerDataAsync(uuid).get(10, TimeUnit.SECONDS);
            logger.finer("[VelocityPerm] Permission load successful for " + playerName);
            loadSuccess = true;


            if (loadSuccess) {
                event.setProvider(this.provider);
                logger.finer("[VelocityPerm] VelocityPermissionProvider injected for " + playerName);

                sessionTrackerServiceOpt.ifPresent(service ->
                        service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE)
                );
                logger.info("[VelocityPerm] Session state set to ONLINE for " + playerName + " after permission setup.");
            }

        } catch (TimeoutException te) {
            logger.log(Level.SEVERE, "[VelocityPerm] Timeout waiting/loading permissions for " + playerName + ". Kicking...");
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_TIMEOUT));
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (CompletionException | ExecutionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            logger.log(Level.SEVERE, "[VelocityPerm] Error during permission load for " + playerName, cause);
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_ERROR));
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[VelocityPerm] General failure injecting Velocity permissions for " + playerName, e);
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_UNEXPECTED));
            if (player.isActive()) player.disconnect(kickMessage);
        } finally {
            if (!loadSuccess && player.isActive()) {
                logger.log(Level.SEVERE, "[VelocityPerm] Permission load/injection failed for {0}. Cleaning Redis session.", playerName);
                sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, playerName));
            }
        }
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
    }
}