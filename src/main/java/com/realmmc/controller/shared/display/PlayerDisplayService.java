package com.realmmc.controller.shared.display;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.permission.PermissionService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.role.Role;

public class PlayerDisplayService {

    private final PermissionService permissionService;

    public PlayerDisplayService() {
        this.permissionService = ServiceRegistry.getInstance().getService(PermissionService.class)
                .orElseThrow(() -> new IllegalStateException("PermissionService não está disponível para o PlayerDisplayService."));
    }

    /**
     * Obtém o cargo de maior peso do jogador, que é usado para formatação.
     * @param profile O perfil do jogador.
     * @return O cargo principal, ou null se não tiver nenhum.
     */
    public Role getRole(Profile profile) {
        return permissionService.getPrimaryRole(profile);
    }

    /**
     * 1. Retorna o nome de utilizador original do jogador.
     * Exemplo: "MrLucas127"
     */
    public String getName(Profile profile) {
        return profile.getName();
    }

    /**
     * 2. Retorna o nome de utilizador sem formatação (tudo em minúsculas).
     * Exemplo: "mrlucas127"
     */
    public String getCleanName(Profile profile) {
        return profile.getName().toLowerCase();
    }

    /**
     * 3. Retorna o nome completo com prefixo e sufixo do cargo principal.
     * Exemplo: "&4[Gerente] &rMrLucas127 &f[DEV]"
     */
    public String getDisplayName(Profile profile) {
        Role primaryRole = getRole(profile);
        if (primaryRole == null) {
            return profile.getName();
        }

        String prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        String suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";
        String color = primaryRole.getColor() != null ? primaryRole.getColor() : "&f";

        if (!prefix.isEmpty() && !prefix.endsWith(" ")) {
            prefix += " ";
        }
        if (!suffix.isEmpty() && !suffix.startsWith(" ")) {
            suffix = " " + suffix;
        }

        return prefix + color + profile.getName() + suffix;
    }

    /**
     * 4. Retorna apenas o prefixo e o sufixo combinados do cargo principal.
     * Exemplo: "&4[Gerente] &f[DEV]"
     */
    public String getPrefix(Profile profile) {
        Role primaryRole = getRole(profile);
        if (primaryRole == null) {
            return "";
        }

        String prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        String suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";

        String result = prefix;
        if (!prefix.isEmpty() && !suffix.isEmpty()) {
            result += " ";
        }
        result += suffix;

        return result.trim();
    }

    /**
     * 5. Retorna o nome de utilizador com a cor do seu cargo principal.
     * Exemplo: "&4MrLucas127"
     */
    public String getColorDisplayName(Profile profile) {
        Role primaryRole = getRole(profile);
        if (primaryRole == null || primaryRole.getColor() == null) {
            return profile.getName();
        }
        return primaryRole.getColor() + profile.getName();
    }
}