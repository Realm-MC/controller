package com.realmmc.controller.shared.profile;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.permission.PermissionService;
import com.realmmc.controller.shared.role.DefaultRole;
import com.realmmc.controller.shared.role.Role;

import java.util.Optional;

/**
 * Serviço responsável por formatar nomes de jogadores com base nos seus perfis e cargos.
 */
public class PlayerDisplayService {

    private final PermissionService permissionService;

    public PlayerDisplayService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public static Optional<PlayerDisplayService> getInstance() {
        return ServiceRegistry.getInstance().getService(PlayerDisplayService.class);
    }

    /**
     * Obtém o nome de exibição do jogador (como guardado no perfil).
     * Ex: "xxxlc"
     */
    public String getDisplayName(Profile profile) {
        return (profile != null) ? profile.getName() : "Unknown";
    }

    /**
     * Obtém o nome de utilizador (login) do jogador (minúsculas).
     * Ex: "xxxlc"
     */
    public String getUsername(Profile profile) {
        return (profile != null) ? profile.getUsername() : "unknown";
    }

    /**
     * Obtém o nome formatado completo (prefixo + nome + sufixo) com base no cargo principal.
     * Ex: "<gold>[Master] </gold>xxxlc"
     */
    public String getFormattedName(Profile profile) {
        if (profile == null) return "Unknown";

        Role primaryRole = permissionService.getPrimaryRole(profile);
        if (primaryRole == null) {
            primaryRole = DefaultRole.DEFAULT.toRole();
        }


        String prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        String suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";
        String name = profile.getName();

        if (!prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix += "";
        }
        if (!suffix.isEmpty() && !suffix.startsWith(" ")) {
            suffix = "" + suffix;
        }


        return prefix + name + suffix;
    }

    /**
     * Obtém apenas os afixos (prefixo + sufixo) do cargo principal.
     * Ex: "<gold>[Master] </gold>"
     */
    public String getAffixes(Profile profile) {
        if (profile == null) return "";

        Role primaryRole = permissionService.getPrimaryRole(profile);
        if (primaryRole == null) {
            primaryRole = DefaultRole.DEFAULT.toRole();
        }

        String prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        String suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";

        if (!prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix += "";
        }
        if (!suffix.isEmpty() && !suffix.startsWith(" ")) {
            suffix = "" + suffix;
        }


        return prefix + suffix;
    }

    /**
     * Obtém o nome do jogador com a cor do seu cargo principal.
     * Ex: "<gold>xxxlc"
     */
    public String getColorizedName(Profile profile) {
        if (profile == null) return "<gray>Unknown";

        Role primaryRole = permissionService.getPrimaryRole(profile);
        if (primaryRole == null) {
            primaryRole = DefaultRole.DEFAULT.toRole();
        }

        String colorTag = primaryRole.getColor() != null && !primaryRole.getColor().isEmpty()
                ? primaryRole.getColor()
                : "<gray>";

        return colorTag + profile.getName();
    }
}