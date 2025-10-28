package com.realmmc.controller.modules.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore; // Importar JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.realmmc.controller.shared.role.Role;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Armazena os dados de permissão calculados e metadados relevantes para a sessão de um jogador online.
 * (Modificado para usar 'long' para timestamp, removendo a dependência jsr310)
 */
@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerSessionData {

    private final UUID uuid;
    private final Role primaryRole;
    private final Set<String> effectivePermissions;
    private final String prefix;
    private final String suffix;

    // <<< CORREÇÃO: Trocado Instant por long >>>
    private final long calculatedAtTimestamp;
    // <<< FIM CORREÇÃO >>>

    /**
     * Construtor principal usado pela lógica.
     */
    public PlayerSessionData(UUID uuid, Role primaryRole, Set<String> effectivePermissions) {
        if (primaryRole == null) {
            throw new IllegalArgumentException("Primary Role cannot be null for PlayerSessionData");
        }
        if (effectivePermissions == null) {
            effectivePermissions = Collections.emptySet();
        }

        this.uuid = uuid;
        this.primaryRole = primaryRole;
        this.effectivePermissions = Set.copyOf(effectivePermissions);
        this.prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        this.suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";
        // <<< CORREÇÃO: Salva o timestamp como long >>>
        this.calculatedAtTimestamp = Instant.now().toEpochMilli();
    }

    /**
     * Construtor para desserialização (JSON).
     */
    @JsonCreator
    public PlayerSessionData(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("primaryRole") Role primaryRole,
            @JsonProperty("effectivePermissions") Set<String> effectivePermissions,
            @JsonProperty("prefix") String prefix,
            @JsonProperty("suffix") String suffix,
            // <<< CORREÇÃO: Recebe o long do JSON >>>
            @JsonProperty("calculatedAtTimestamp") long calculatedAtTimestamp) {

        if (primaryRole == null) {
            throw new IllegalArgumentException("Primary Role cannot be null for deserialization");
        }

        this.uuid = uuid;
        this.primaryRole = primaryRole;
        this.effectivePermissions = (effectivePermissions != null) ? Set.copyOf(effectivePermissions) : Collections.emptySet();
        this.prefix = (prefix != null) ? prefix : (primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "");
        this.suffix = (suffix != null) ? suffix : (primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "");
        // <<< CORREÇÃO: Define o long ou usa o tempo atual como fallback >>>
        this.calculatedAtTimestamp = (calculatedAtTimestamp > 0) ? calculatedAtTimestamp : Instant.now().toEpochMilli();
    }

    /**
     * <<< CORREÇÃO: Helper getter para converter o long de volta para Instant >>>
     * Retorna o timestamp como um objeto Instant para compatibilidade com
     * a lógica de verificação de expiração do cache no RoleService.
     * @return Instant
     */
    @JsonIgnore // Diz ao Jackson para IGNORAR este método durante a serialização/desserialização
    public Instant getCalculatedAt() {
        return Instant.ofEpochMilli(calculatedAtTimestamp);
    }

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) { return false; }
        String permLower = permission.toLowerCase();
        if (effectivePermissions.contains("-" + permLower)) { return false; }
        if (effectivePermissions.contains(permLower)) { return true; }
        if (effectivePermissions.contains("*")) { return true; }
        String[] parts = permLower.split("\\.");
        StringBuilder builder = new StringBuilder(permLower.length());
        for (int i = 0; i < parts.length - 1; i++) {
            builder.append(parts[i]).append(".");
            if (effectivePermissions.contains(builder + "*")) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getAllPermissions() {
        return effectivePermissions;
    }
}