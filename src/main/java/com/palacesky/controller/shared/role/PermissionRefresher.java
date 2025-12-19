package com.palacesky.controller.shared.role;

import java.util.UUID;

/**
 * Interface para abstrair a notificação ou atualização de permissões
 * de um jogador online numa plataforma específica após uma mudança
 * (ex: recebida via Redis).
 */
@FunctionalInterface // Indica que só tem um método abstrato
public interface PermissionRefresher {

    /**
     * Tenta notificar ou forçar a atualização das permissões para um jogador
     * que pode estar online nesta instância do servidor/proxy.
     * * A implementação desta interface tratará da lógica específica da plataforma
     * (Spigot, Velocity, etc.) para encontrar o jogador e aplicar a atualização.
     *
     * @param playerUuid O UUID do jogador cujas permissões foram atualizadas.
     */
    void refreshPlayerPermissions(UUID playerUuid);

}