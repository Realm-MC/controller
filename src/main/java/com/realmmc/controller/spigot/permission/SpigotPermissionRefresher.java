package com.realmmc.controller.spigot.permission;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.role.PermissionRefresher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotPermissionRefresher implements PermissionRefresher {

    private final Logger logger;
    private final RoleService roleService;
    private final Plugin plugin;

    public SpigotPermissionRefresher(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        try { this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class); }
        catch (IllegalStateException e) { logger.log(Level.SEVERE, "[PermissionRefresher] Critical Error: RoleService not found!", e); throw new RuntimeException("Failed: RoleService is missing.", e); }
        logger.info("[PermissionRefresher] SpigotPermissionRefresher initialized.");
    }

    @Override
    public void refreshPlayerPermissions(UUID playerUuid) {
        if (playerUuid == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);

            if (player != null && player.isOnline()) {
                logger.info("[PermissionRefresher] Received refresh request for online Spigot player: " + player.getName() + ". Dispatching async recalculation...");

                roleService.loadPlayerDataAsync(playerUuid)
                        .whenComplete((sessionData, error) -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;

                                if (error != null) {
                                    logger.log(Level.SEVERE, "[PermissionRefresher] Error during permission recalculation for " + player.getName(), error);
                                } else {
                                    logger.fine("[PermissionRefresher] Recalculation complete for " + player.getName() + ". Forcing Bukkit re-evaluation...");
                                    try {
                                        player.recalculatePermissions();
                                        logger.fine("[PermissionRefresher] Bukkit recalculatePermissions() called for " + player.getName());
                                    } catch (Exception e) {
                                        logger.log(Level.WARNING, "[PermissionRefresher] Error calling recalculatePermissions() for " + player.getName(), e);
                                    }
                                }
                            });
                        });
            } else {
                logger.finest("[PermissionRefresher] Refresh request for player not online on this server: " + playerUuid);
            }
        });
    }
}