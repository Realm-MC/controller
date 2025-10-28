package com.realmmc.controller.spigot.permission;

import com.realmmc.controller.modules.role.RoleService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;

import java.util.UUID;

public class CustomPermissible extends PermissibleBase {

    private final UUID uuid;
    private final RoleService roleService;
    private final Player player; // Mantemos uma referência para o 'isOp()'

    public CustomPermissible(Player player, RoleService roleService) {
        super(player); // Passa o jogador para a classe base
        this.player = player;
        this.uuid = player.getUniqueId();
        this.roleService = roleService;
    }

    /**
     * Este é o método principal que o Spigot chama para verificar permissões.
     * Nós substituímo-lo para usar o nosso RoleService.
     */
    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }

        // 1. Um Operador (OP) deve ter sempre todas as permissões
        if (player.isOp()) {
            return true;
        }

        // 2. Chama o nosso RoleService para verificar
        return roleService.hasPermission(uuid, permission);
    }

    /**
     * Versão sobrecarregada que também precisa ser substituída.
     */
    @Override
    public boolean hasPermission(Permission perm) {
        if (perm == null) {
            return false;
        }
        return hasPermission(perm.getName());
    }
}