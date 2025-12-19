package com.palacesky.controller.spigot.permission;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.role.PermissionRefresher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
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
                logger.info("[SpigotPerm] Refresh solicitado para " + player.getName() + ". Invalidando cache...");

                roleService.invalidateSession(playerUuid);

                roleService.loadPlayerDataAsync(playerUuid).thenAccept(sessionData -> {

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        player.recalculatePermissions();

                        player.updateCommands();

                        if (player.isOp() && sessionData != null &&
                                !sessionData.getPrimaryRole().getType().equals(com.palacesky.controller.shared.role.RoleType.STAFF)) {
                            player.setOp(false);
                            logger.info("[SpigotPerm] OP removido de " + player.getName());
                        }

                        logger.info("[SpigotPerm] Permissões e Comandos atualizados para " + player.getName());
                    });
                });
            }
        });
    }
}