package com.realmmc.controller.shared.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRole {

    public enum Status {
        ACTIVE,     // O role está ativo e contribui para permissões
        EXPIRED,    // O tempo do role expirou
        REMOVED     // O role foi removido manualmente (via /role remove ou /role clear)
        // PAUSED não é um status aqui, é um boolean separado
    }

    private String roleName;

    @Builder.Default
    private long addedAt = System.currentTimeMillis();

    @Builder.Default
    private Long expiresAt = null; // null = permanente

    @Builder.Default
    private boolean paused = false;

    @Builder.Default
    private Long pausedTimeRemaining = null; // Tempo restante quando pausado

    @Builder.Default
    private Status status = Status.ACTIVE; // <<<--- NOVO CAMPO STATUS

    @Builder.Default
    private Long removedAt = null; // <<<--- NOVO CAMPO: Quando foi removido (opcional)

    public boolean isPermanent() {
        return expiresAt == null && pausedTimeRemaining == null;
    }

    /**
     * Verifica se o grupo expirou naturalmente (NÃO considera status REMOVED).
     * Um grupo pausado NÃO é considerado expirado aqui.
     * @return true se o tempo passou E não está pausado, false caso contrário.
     */
    public boolean hasExpiredTime() {
        return !isPaused() && expiresAt != null && System.currentTimeMillis() > expiresAt;
    }

    // Deprecated: Usar hasExpiredTime() e verificar status separadamente.
    // Manter por compatibilidade temporária se necessário, mas ajustar lógica.
    @Deprecated
    public boolean hasExpired() {
        return hasExpiredTime(); // Simplificado, cálculo principal verificará Status
    }

    /**
     * Verifica se o role está atualmente ativo para cálculo de permissões.
     * @return true se ACTIVE, não pausado e não expirado.
     */
    public boolean isActive() {
        return getStatus() == Status.ACTIVE && !isPaused() && !hasExpiredTime();
    }
}