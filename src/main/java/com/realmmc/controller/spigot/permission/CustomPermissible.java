package com.realmmc.controller.spigot.permission;

import com.realmmc.controller.modules.role.RoleService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;

import java.util.UUID;

public class CustomPermissible extends PermissibleBase {

    private final UUID uuid;
    private final RoleService roleService;
    private final Player player;

    public CustomPermissible(Player player, RoleService roleService) {
        super(player);
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

        if (player.isOp()) {
            return true;
        }

        return roleService.hasPermission(uuid, permission);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        if (perm == null) {
            return false;
        }
        return hasPermission(perm.getName());
    }
}