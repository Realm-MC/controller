package com.palacesky.controller.proxy.permission;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.role.PermissionRefresher;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler; // Import Scheduler

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação Velocity do PermissionRefresher.
 * Dispara o recálculo assíncrono das permissões no RoleService para jogadores online.
 */
public class VelocityPermissionRefresher implements PermissionRefresher {

    private final ProxyServer server;
    private final Logger logger;
    private final RoleService roleService;
    private final Scheduler scheduler; // Velocity Scheduler
    private final Object pluginInstance; // Instância do plugin Velocity (@Plugin)

    // Construtor obtém dependências
    public VelocityPermissionRefresher(ProxyServer server, Object pluginInstance, Logger logger) { // Adicionado pluginInstance
        this.server = server;
        this.pluginInstance = pluginInstance; // Guarda a instância do plugin
        this.logger = logger;
        this.scheduler = server.getScheduler(); // Obtém o scheduler
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            logger.info("VelocityPermissionRefresher inicializado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro Crítico: RoleService não encontrado ao criar VelocityPermissionRefresher!", e);
            throw new RuntimeException("Falha ao inicializar VelocityPermissionRefresher: RoleService ausente.", e);
        }
    }

    @Override
    public void refreshPlayerPermissions(UUID playerUuid) {
        if (playerUuid == null) {
            logger.finest("Pedido de refresh ignorado: UUID nulo.");
            return;
        }

        // Busca o jogador (pode ser feito fora da thread principal no Velocity)
        Optional<Player> playerOpt = server.getPlayer(playerUuid);

        // Verifica se o jogador está online NESTE proxy
        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            logger.info("[Permissions] Recebido pedido de refresh para jogador Velocity online: " + player.getUsername() + ". Disparando recálculo async...");

            // Dispara o recálculo assíncrono no RoleService.
            // O resultado atualizará o sessionCache.
            // O VelocityPermissionProvider lerá o cache atualizado na próxima verificação.
            roleService.loadPlayerDataAsync(playerUuid)
                    .whenCompleteAsync((data, error) -> { // Roda no pool do RoleService
                        // Apenas loga o resultado.
                        if (error != null) {
                            logger.log(Level.SEVERE, "[Permissions] Erro durante recálculo async no refresh para " + player.getUsername() + " (UUID: " + playerUuid + ")", error);
                        } else {
                            logger.fine("[Permissions] Recálculo async concluído com sucesso para " + player.getUsername() + " após refresh.");
                        }
                    }/* , scheduler.asyncExecutor() */); // Pode direcionar para o executor do Velocity se preferir

        } else { // Jogador offline ou em outro proxy
            logger.finest("[Permissions] Recebido pedido de refresh para jogador não online neste proxy: " + playerUuid);
            // O cache de sessão já foi invalidado pelo RoleSyncService.
        }
    }
}