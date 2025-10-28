package com.realmmc.controller.proxy.permission;

import com.realmmc.controller.core.services.ServiceRegistry; // Import ServiceRegistry
import com.realmmc.controller.modules.role.RoleService;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import java.util.logging.Logger; // Import Logger

public class VelocityPermissionProvider implements PermissionProvider {

    private final RoleService roleService;
    // private final Logger logger; // Opcional para debug

    // Construtor modificado para obter RoleService via ServiceRegistry
    public VelocityPermissionProvider(Logger logger) {
        // this.logger = logger; // Opcional
        try {
            // Tenta obter o RoleService ao ser instanciado
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            // logger.info("VelocityPermissionProvider inicializado com RoleService."); // Opcional
        } catch (IllegalStateException e) {
            logger.severe("Erro Crítico: RoleService não encontrado ao criar VelocityPermissionProvider!");
            throw new RuntimeException("Falha ao inicializar VelocityPermissionProvider: RoleService ausente.", e);
        }
    }

    @Override
    public PermissionFunction createFunction(PermissionSubject subject) {
        if (!(subject instanceof Player player)) {
            // logger.finer("Criando função padrão para subject não-Player: " + subject.getClass().getName()); // Opcional
            return permission -> Tristate.UNDEFINED;
        }
        // logger.finer("Criando função de permissão para Player: " + player.getUsername()); // Opcional
        return new PlayerPermissionFunction(player.getUniqueId(), roleService);
    }

    /**
     * Função que verifica permissão para UM jogador específico usando o RoleService.
     */
    private static class PlayerPermissionFunction implements PermissionFunction {
        private final UUID uuid;
        private final RoleService roleService;
        // private final Logger logger; // Opcional

        PlayerPermissionFunction(UUID uuid, RoleService roleService) {
            this.uuid = uuid;
            this.roleService = roleService;
            // this.logger = Logger.getLogger("PlayerPermissionFunction-" + uuid.toString().substring(0, 4)); // Opcional
        }

        @Override
        public Tristate getPermissionValue(String permission) {
            // logger.finest("Verificando permissão: " + permission); // Opcional
            // Chama o hasPermission do RoleService (que usa o cache de sessão)
            boolean hasPerm = roleService.hasPermission(uuid, permission);
            Tristate result = Tristate.fromBoolean(hasPerm);
            // logger.finest("Resultado para '" + permission + "': " + result); // Opcional
            return result;
        }
    }
}