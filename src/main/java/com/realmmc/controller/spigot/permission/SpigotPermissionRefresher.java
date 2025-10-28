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
        catch (IllegalStateException e) { /* ... log e throw ... */ logger.log(Level.SEVERE, "Erro Crítico: RoleService não encontrado!", e); throw new RuntimeException("Falha: RoleService ausente.", e); }
        logger.info("SpigotPermissionRefresher inicializado.");
    }

    @Override
    public void refreshPlayerPermissions(UUID playerUuid) {
        if (playerUuid == null) return;

        // Delega para a thread principal do Bukkit
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);

            if (player != null && player.isOnline()) {
                logger.info("[Permissions] Recebido pedido de refresh para " + player.getName() + ". Disparando recálculo async...");

                // Dispara recálculo assíncrono
                roleService.loadPlayerDataAsync(playerUuid)
                        .whenComplete((sessionData, error) -> { // Roda no pool do RoleService
                            // Agenda a parte final na thread principal do Bukkit
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return; // Verifica se ainda está online

                                if (error != null) {
                                    logger.log(Level.SEVERE, "Erro durante recálculo de permissões no refresh para " + player.getName(), error);
                                } else {
                                    logger.fine("Recálculo concluído para " + player.getName() + ". Forçando reavaliação do Bukkit...");
                                    try {
                                        // <<< ================= CORREÇÃO AQUI ================= >>>
                                        player.recalculatePermissions();
                                        // <<< =============================================== >>>
                                        logger.fine("Bukkit recalculatePermissions() chamado para " + player.getName());
                                        // Opcional: player.updateCommands(); // Se comandos baseados em perm não atualizarem
                                    } catch (Exception e) {
                                        logger.log(Level.WARNING, "Erro ao chamar recalculatePermissions() para " + player.getName(), e);
                                    }
                                }
                            }); // Fim do runTask interno
                        }); // Fim do whenComplete

            } else {
                logger.finest("[Permissions] Pedido de refresh para jogador offline/outro servidor: " + playerUuid);
            }
        }); // Fim do runTask externo
    }
}