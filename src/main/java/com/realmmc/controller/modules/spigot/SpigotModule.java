package com.realmmc.controller.modules.spigot;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.services.SessionService;
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.role.RoleKickHandler; // <<< IMPORTAR
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import com.realmmc.controller.spigot.permission.SpigotPermissionInjector;
import com.realmmc.controller.spigot.permission.SpigotPermissionRefresher;
import com.realmmc.controller.spigot.sounds.SpigotSoundPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player; // <<< IMPORTAR
import org.bukkit.plugin.Plugin;
import org.bukkit.event.HandlerList;

import java.util.UUID; // <<< IMPORTAR
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotModule extends AbstractCoreModule {
    private final Plugin plugin;
    private SessionService sessionServiceInstance;
    private SpigotPermissionInjector permissionInjectorInstance;

    public SpigotModule(Plugin plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
    }

    @Override public String getName() { return "SpigotModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo específico para funcionalidades Spigot (v2)."; }

    @Override public int getPriority() { return 50; } // Carrega por último

    @Override public String[] getDependencies() {
        return new String[]{
                "RoleModule", "SchedulerModule", "Command", "Preferences", "Particle"
        };
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando serviços e listeners específicos do Spigot (v2)...");

        // 1. Registar SoundPlayer
        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new SpigotSoundPlayer());
            logger.info("SpigotSoundPlayer registrado.");
        } catch (Exception e) { /* ... log ... */ }

        // 2. Registar Comandos e Listeners
        try { CommandManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar comandos Spigot!", e); }
        try { ListenersManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar listeners Spigot!", e); }


        // --- INTEGRAÇÃO PERMISSÕES E SESSÃO (v2) ---

        // 3. SessionService
        try {
            this.sessionServiceInstance = new SessionService(logger);
            Bukkit.getServer().getPluginManager().registerEvents(sessionServiceInstance, plugin);
            logger.info("SessionService (Listener) registrado.");
        } catch (Exception e) { /* ... log ... */ this.sessionServiceInstance = null; }

        // 4. SpigotPermissionInjector
        if (this.sessionServiceInstance != null) {
            try {
                this.permissionInjectorInstance = new SpigotPermissionInjector(plugin, logger);
                Bukkit.getServer().getPluginManager().registerEvents(permissionInjectorInstance, plugin);
                logger.info("SpigotPermissionInjector (Listener) registrado.");
            } catch (Exception e) { /* ... log ... */ this.permissionInjectorInstance = null; }
        } else {
            logger.severe("SpigotPermissionInjector não registrado porque SessionService falhou ao inicializar.");
        }

        // 5. SpigotPermissionRefresher e RoleKickHandler
        ServiceRegistry.getInstance().getService(RoleService.class).ifPresentOrElse(roleService -> {
            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher spigotRefresher = new SpigotPermissionRefresher(plugin, logger);
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, spigotRefresher);
                    logger.info("SpigotPermissionRefresher registrado no ServiceRegistry.");
                } catch (Exception e) { /* ... log ... */ }

                // <<< CORREÇÃO: Inicializar o KickHandler AQUI >>>
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
                    logger.info("RoleKickHandler inicializado para plataforma Spigot.");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Falha ao inicializar RoleKickHandler no SpigotModule!", e);
                }
                // <<< FIM CORREÇÃO >>>

            } else {
                logger.severe("SpigotPermissionRefresher (e KickHandler) não registrado porque o SpigotPermissionInjector falhou.");
            }
        }, () -> {
            logger.severe("RoleService NÃO detectado. Integração de permissões Spigot não será configurada.");
        });
        // --- FIM INTEGRAÇÃO (v2) ---

        logger.info("SpigotModule habilitado com sucesso.");
    }

    @Override
    protected void onDisable() {
        logger.info("Desabilitando SpigotModule...");

        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        logger.info("Serviços Spigot desregistrados.");

        if (sessionServiceInstance != null) {
            try { HandlerList.unregisterAll(sessionServiceInstance); }
            catch (Exception e) { /* ... log ... */ }
            sessionServiceInstance = null;
        }
        if (permissionInjectorInstance != null) {
            try {
                HandlerList.unregisterAll(permissionInjectorInstance);
                permissionInjectorInstance.cleanupOnDisable();
            }
            catch (Exception e) { /* ... log ... */ }
            permissionInjectorInstance = null;
        }

        try { HandlerList.unregisterAll(plugin); logger.info("Outros listeners Spigot desregistrados."); }
        catch (Exception e) { /* ... log ... */ }

        // O RoleKickHandler.shutdown() é chamado pelo RoleModule.onDisable()

        logger.info("SpigotModule desabilitado.");
    }
}