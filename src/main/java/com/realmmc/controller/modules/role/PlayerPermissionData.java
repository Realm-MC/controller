package com.realmmc.controller.modules.role;

import com.realmmc.controller.shared.role.Role;
import java.util.Collections; // Import para Collections.unmodifiableSet
import java.util.Set;
import java.util.UUID;

/**
 * Um objeto de cache que armazena as permissões efetivas de um jogador,
 * seu grupo primário e outros metadados relevantes.
 * Instâncias desta classe devem ser consideradas imutáveis após a criação.
 */
public class PlayerPermissionData {

    private final UUID uuid;
    private final Role primaryRole;
    // O conjunto de permissões efetivas (em minúsculas)
    private final Set<String> permissions;

    /**
     * Construtor para PlayerPermissionData.
     * @param uuid O UUID do jogador.
     * @param primaryRole O grupo primário calculado para o jogador.
     * @param permissions O conjunto de todas as permissões efetivas (já calculadas, em minúsculas).
     */
    public PlayerPermissionData(UUID uuid, Role primaryRole, Set<String> permissions) {
        // Validação básica dos argumentos
        if (uuid == null) {
            // Lançar exceção ou logar, dependendo da política de erro. UUID é essencial.
            // throw new IllegalArgumentException("UUID cannot be null for PlayerPermissionData");
            // Por agora, logamos um aviso, mas o objeto pode ficar inconsistente.
            System.err.println("WARN: PlayerPermissionData criado com UUID nulo!");
        }
        if (primaryRole == null) {
            // É crucial ter um primaryRole, mesmo que seja o 'default'.
            // Lançar exceção seria mais seguro.
            throw new IllegalArgumentException("Primary Role cannot be null for PlayerPermissionData");
        }
        if (permissions == null) {
            // Garante que o set nunca é nulo, usa um set vazio se necessário.
            permissions = Collections.emptySet();
            System.err.println("WARN: PlayerPermissionData criado com conjunto de permissões nulo para UUID: " + uuid);
        }

        this.uuid = uuid;
        this.primaryRole = primaryRole;
        // Armazena uma cópia imutável do conjunto de permissões para segurança
        this.permissions = Set.copyOf(permissions);
    }

    /**
     * Obtém o grupo primário do jogador.
     * @return O objeto Role representando o grupo primário.
     */
    public Role getPrimaryRole() {
        // Retorna o objeto Role diretamente. Considerar retornar uma cópia se Role for mutável.
        return primaryRole;
    }

    /**
     * Obtém o prefixo de chat associado ao grupo primário.
     * Retorna uma string vazia se o prefixo for nulo.
     * @return O prefixo do grupo primário.
     */
    public String getPrefix() {
        return primaryRole.getPrefix() != null ? primaryRole.getPrefix() : "";
    }

    /**
     * Obtém o sufixo de chat associado ao grupo primário.
     * Retorna uma string vazia se o sufixo for nulo.
     * @return O sufixo do grupo primário.
     */
    public String getSuffix() {
        return primaryRole.getSuffix() != null ? primaryRole.getSuffix() : "";
    }

    /**
     * Verifica se o jogador tem uma permissão específica.
     * A verificação é case-insensitive e suporta wildcards básicos (`*` e `node.*`).
     * @param permission A string da permissão a ser verificada.
     * @return true se o jogador tiver a permissão, false caso contrário.
     */
    public boolean hasPermission(String permission) {
        // Validação inicial
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // Converte a permissão requisitada para minúsculas para comparação
        String permLower = permission.toLowerCase();

        // 1. Verificação de permissão exata (mais comum, verifica primeiro)
        if (permissions.contains(permLower)) {
            return true;
        }

        // 2. Verificação do wildcard global "*" (concede todas as permissões)
        if (permissions.contains("*")) {
            return true;
        }

        // 3. Verificação de permissões negativas explícitas (ex: "-alguma.coisa")
        // Se o jogador tem a permissão negativa, ele NÃO tem a permissão, independentemente de wildcards.
        if (permissions.contains("-" + permLower)) {
            return false;
        }

        // 4. Verificação de wildcards parciais (ex: tem "a.b.*" e pede "a.b.c")
        // Itera sobre as partes da permissão pedida, construindo nós pais com wildcard.
        String[] parts = permLower.split("\\."); // Escapa o ponto para regex
        StringBuilder builder = new StringBuilder(permLower.length());

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            // Verifica se o jogador tem o nó pai com wildcard (ex: "a.b.*")
            if (permissions.contains(builder.toString() + ".*")) {
                // Agora, verifica se há uma negação mais específica para a permissão pedida
                // Ex: tem "a.b.*" mas também tem "-a.b.c" e pediu "a.b.c" -> retorna false
                if (!permissions.contains("-" + permLower)) {
                    return true; // Tem o wildcard e não tem negação específica
                } else {
                    return false; // Tem o wildcard mas também tem negação específica
                }
            }
            // Adiciona o ponto para o próximo nível (exceto no último)
            if (i < parts.length - 1) {
                builder.append(".");
            }
        }

        // 5. Se nenhuma das condições acima foi satisfeita, o jogador não tem a permissão.
        return false;
    }

    /**
     * Retorna uma cópia do conjunto de todas as permissões efetivas calculadas
     * para este jogador (incluindo as herdadas e negativas).
     * @return Um Set não modificável contendo as strings de permissão (em minúsculas).
     */
    public Set<String> getAllPermissions() {
        // Retorna o conjunto imutável armazenado internamente
        return permissions;
    }

    // Opcional: Sobrescrever equals() e hashCode() se for usar em coleções
    // que dependem desses métodos (ex: HashSet<PlayerPermissionData>).
    // Por ser um objeto de cache, geralmente não é necessário.

    // Opcional: toString() para debugging
    @Override
    public String toString() {
        return "PlayerPermissionData{" +
                "uuid=" + uuid +
                ", primaryRole=" + (primaryRole != null ? primaryRole.getName() : "null") +
                ", permissionsCount=" + permissions.size() +
                '}';
    }
}