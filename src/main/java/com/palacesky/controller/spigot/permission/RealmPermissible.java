package com.palacesky.controller.spigot.permission;

import com.palacesky.controller.modules.role.RoleService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;

public class RealmPermissible extends PermissibleBase {
    private final Player player;
    private final RoleService roleService;
    private final UUID uuid;

    public RealmPermissible(Player player, RoleService roleService) {
        super(player);
        this.player = player;
        this.roleService = roleService;
        this.uuid = player.getUniqueId();
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) return false;

        boolean isSystemLoaded = roleService.getSessionDataFromCache(uuid).isPresent();
        boolean isControllerCommand = permission.startsWith("controller.") || permission.startsWith("group.");

        if (roleService.hasPermission(uuid, permission)) {
            return true;
        }

        if (isControllerCommand) {
            if (!isSystemLoaded) {
                return false;
            }
            return false;
        }

        return super.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return hasPermission(perm.getName());
    }


    @Override
    public boolean isPermissionSet(String name) {
        return roleService.hasPermission(uuid, name) || super.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return isPermissionSet(perm.getName());
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return super.getEffectivePermissions();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return super.addAttachment(plugin, name, value);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return super.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return super.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return super.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        super.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        super.recalculatePermissions();
    }

    @Override
    public synchronized void clearPermissions() {
        super.clearPermissions();
    }
}