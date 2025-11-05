package com.realmmc.controller.spigot.permission;

import com.realmmc.controller.modules.role.RoleService;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment; // Import necessário
import org.bukkit.permissions.PermissionAttachmentInfo; // Import necessário
import org.bukkit.plugin.Plugin; // Import necessário

import java.util.Set; // Import necessário
import java.util.UUID;
import java.util.logging.Logger; // Opcional para logging interno

/**
 * Implementação customizada de PermissibleBase que delega a verificação
 * de permissões para o RoleService, usando o cache de sessão.
 */
public class RealmPermissible extends PermissibleBase {
    private final Player player; // Mantém referência para isOp() e outros métodos base
    private final RoleService roleService;
    private final UUID uuid;
    // private final Logger logger; // Opcional: para debug interno

    public RealmPermissible(Player player, RoleService roleService) {
        super(player); // Passa o jogador (ServerOperator) para a superclasse
        this.player = player;
        this.roleService = roleService;
        this.uuid = player.getUniqueId();
        // this.logger = Logger.getLogger("RealmPermissible"); // Opcional
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }

        // 1. OP sempre tem permissão (respeita comportamento padrão Bukkit)
        if (super.isOp()) { // Delega para o isOp() original do Player via superclasse
            return true;
        }

        // 2. Verifica se a permissão foi definida explicitamente via API Bukkit (attachments)
        // Isso permite compatibilidade com outros plugins que manipulam permissões diretamente
        if (super.isPermissionSet(permission)) {
            return super.hasPermission(permission); // Usa a lógica padrão do PermissibleBase
        }

        // 3. Se não foi definida explicitamente e não é OP, delega para o RoleService
        // logger.finer("Checking permission '" + permission + "' for " + player.getName() + " via RoleService"); // Opcional
        boolean result = roleService.hasPermission(uuid, permission);
        // logger.finer("Result for '" + permission + "': " + result); // Opcional
        return result;
    }

    @Override
    public boolean hasPermission(Permission perm) {
        // Usa a implementação de hasPermission(String) acima
        return hasPermission(perm.getName());
    }

    // --- Delegação de Métodos Importantes para a Superclasse ---
    // É crucial delegar métodos que manipulam o estado interno (como attachments)
    // para a implementação padrão do PermissibleBase para manter a compatibilidade.

    @Override
    public boolean isPermissionSet(String name) {
        return super.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return super.isPermissionSet(perm);
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

    // --- Métodos isOp() e setOp() ---
    // Estes são especiais e geralmente delegam diretamente ao Player subjacente

    @Override
    public boolean isOp() {
        // Importante: NÃO chame player.isOp() diretamente para evitar recursão infinita
        // PermissibleBase já delega corretamente para o Servable (Player)
        return super.isOp();
    }

    @Override
    public void setOp(boolean value) {
        // Importante: NÃO chame player.setOp() diretamente
        // PermissibleBase já delega corretamente
        super.setOp(value);
    }
}