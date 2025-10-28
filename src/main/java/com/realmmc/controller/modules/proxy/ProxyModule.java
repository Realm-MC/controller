package com.realmmc.controller.modules.proxy;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
import com.realmmc.controller.proxy.permission.VelocityPermissionInjector;
import com.realmmc.controller.proxy.permission.VelocityPermissionRefresher;
import com.realmmc.controller.proxy.sounds.VelocitySoundPlayer;
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.role.RoleKickHandler; // <<< IMPORTAR
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage; // <<< IMPORTAR
import java.util.UUID; // <<< IMPORTAR

import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyModule extends AbstractCoreModule {
    private final ProxyServer server;
    private final Object pluginInstance;
    private VelocityPermissionInjector permissionInjectorInstance;

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
                "RoleModule",
                "SchedulerModule",
                "Command",
                "Preferences"
        };
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando serviços e listeners específicos do Velocity (v2)...");

        // 1. Registar SoundPlayer
        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new VelocitySoundPlayer());
            logger.info("VelocitySoundPlayer registrado.");
        } catch (Exception e) { /* ... log ... */ }

        // 2. Registar Comandos e Listeners
        try { CommandManager.registerAll(pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar comandos Velocity!", e); }
        try { ListenersManager.registerAll(server, pluginInstance); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar listeners Velocity!", e); }

        // --- INTEGRAÇÃO PERMISSÕES (v2) ---
        ServiceRegistry.getInstance().getService(RoleService.class).ifPresentOrElse(roleService -> {
            logger.info("RoleService detectado. Configurando integração de permissões Velocity...");

            // 3a. Injetor
            try {
                this.permissionInjectorInstance = new VelocityPermissionInjector(pluginInstance, logger);
                server.getEventManager().register(pluginInstance, permissionInjectorInstance);
                logger.info("VelocityPermissionInjector registrado como listener.");
            } catch (Exception e) { /* ... log ... */ this.permissionInjectorInstance = null; }

            // 3b. Refresher
            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher velocityRefresher = new VelocityPermissionRefresher(server, pluginInstance, logger);
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, velocityRefresher);
                    logger.info("VelocityPermissionRefresher registrado no ServiceRegistry.");
                } catch (Exception e) { /* ... log ... */ }
            } else { logger.severe("VelocityPermissionRefresher não registrado..."); }

            // <<< CORREÇÃO: Inicializar o KickHandler AQUI >>>
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
            // <<< FIM CORREÇÃO >>>

        }, () -> {
            logger.severe("RoleService NÃO detectado. Integração de permissões Velocity não será configurada.");
        });
        // --- FIM INTEGRAÇÃO (v2) ---

        logger.info("ProxyModule habilitado.");
    }

    @Override
    protected void onDisable() {
        logger.info("Desabilitando ProxyModule...");

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

        // O RoleKickHandler.shutdown() é chamado pelo RoleModule.onDisable()

        logger.info("ProxyModule desabilitado.");
    }
}