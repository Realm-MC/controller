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
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
    }

    @Override
    public void refreshPlayerPermissions(UUID playerUuid) {
        if (playerUuid == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);

            if (player != null && player.isOnline()) {
                roleService.loadPlayerDataAsync(playerUuid).thenAccept(sessionData -> {

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        player.recalculatePermissions();
                        player.updateCommands();

                        logger.info("[PermissionRefresher] Permissões aplicadas e atualizadas para " + player.getName());
                    });
                });
            }
        });
    }
}