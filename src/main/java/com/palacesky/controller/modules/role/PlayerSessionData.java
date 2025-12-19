package com.palacesky.controller.modules.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.palacesky.controller.shared.role.Role;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.*; // Import HashSet e Collections

@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // Ignora campos desconhecidos
public class PlayerSessionData {

    private final UUID uuid;
    private final Role primaryRole;
    // <<< CORREÇÃO: Mudar para HashSet para compatibilidade com Jackson >>>
    private final HashSet<String> effectivePermissions;
    // <<< FIM CORREÇÃO >>>
    private final String prefix;
    private final String suffix;
    private final long calculatedAtTimestamp;

    /** Construtor principal usado pela lógica (calculatePermissionsExplicit). */
    public PlayerSessionData(UUID uuid, Role primaryRole, Set<String> effectivePermissions) {
        if (primaryRole == null) {
            throw new IllegalArgumentException("Primary Role cannot be null for PlayerSessionData");
        }
        this.uuid = uuid;
        this.primaryRole = primaryRole;
        // <<< CORREÇÃO: Inicializa o HashSet a partir do Set recebido >>>
        this.effectivePermissions = new HashSet<>(effectivePermissions != null ? effectivePermissions : Collections.emptySet());
        // <<< FIM CORREÇÃO >>>
        this.prefix = primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
        this.suffix = primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";
        this.calculatedAtTimestamp = Instant.now().toEpochMilli();
    }

    /**
     * Construtor para desserialização (JSON).
     */
    @JsonCreator
    public PlayerSessionData(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("primaryRole") Role primaryRole,
            // <<< CORREÇÃO: Aceita HashSet diretamente >>>
            @JsonProperty("effectivePermissions") HashSet<String> effectivePermissions,
            @JsonProperty("prefix") String prefix,
            @JsonProperty("suffix") String suffix,
            @JsonProperty("calculatedAtTimestamp") long calculatedAtTimestamp) {

        if (primaryRole == null) {
            throw new IllegalArgumentException("Primary Role cannot be null for deserialization");
        }
        this.uuid = uuid;
        this.primaryRole = primaryRole;
        // <<< CORREÇÃO: Atribui o HashSet recebido (ou um vazio se nulo) >>>
        this.effectivePermissions = (effectivePermissions != null) ? effectivePermissions : new HashSet<>();
        // <<< FIM CORREÇÃO >>>
        this.prefix = (prefix != null) ? prefix : (primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "");
        this.suffix = (suffix != null) ? suffix : (primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "");
        this.calculatedAtTimestamp = (calculatedAtTimestamp > 0) ? calculatedAtTimestamp : Instant.now().toEpochMilli();
    }

    @JsonIgnore
    public Instant getCalculatedAt() {
        return Instant.ofEpochMilli(calculatedAtTimestamp);
    }

    /**
     * Lógica de verificação de permissão (ajustada para clareza).
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) { return false; }
        if (effectivePermissions == null) { return false; } // Checagem de segurança
        String permLower = permission.toLowerCase();

        // 1. Negação explícita tem prioridade máxima
        if (effectivePermissions.contains("-" + permLower)) { return false; }
        // 2. Permissão exata
        if (effectivePermissions.contains(permLower)) { return true; }
        // 3. Wildcard global
        if (effectivePermissions.contains("*")) { return true; }
        // 4. Wildcards parciais (a.b.* -> a.b.c)
        String[] parts = permLower.split("\\.");
        StringBuilder builder = new StringBuilder(permLower.length());
        for (int i = 0; i < parts.length - 1; i++) { // Itera até o penúltimo
            builder.append(parts[i]).append(".");
            if (effectivePermissions.contains(builder + "*")) {
                return true;
            }
        }
        // 5. Nenhuma permissão encontrada
        return false;
    }

    /**
     * Retorna uma CÓPIA IMUTÁVEL do conjunto de permissões para uso externo seguro.
     */
    @JsonIgnore
    public Set<String> getAllPermissions() {
        return effectivePermissions != null ? Set.copyOf(effectivePermissions) : Collections.emptySet();
    }

    @Override
    public String toString() {
        return "PlayerSessionData{" +
                "uuid=" + uuid +
                ", primaryRole=" + (primaryRole != null ? primaryRole.getName() : "null") +
                ", permissionsCount=" + (effectivePermissions != null ? effectivePermissions.size() : 0) +
                ", calculatedAt=" + getCalculatedAt() +
                '}';
    }
}