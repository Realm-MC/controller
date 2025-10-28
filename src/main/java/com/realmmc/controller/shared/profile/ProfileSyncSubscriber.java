package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService; // Para invalidar sessão após sync
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences; // Import MongoSequences
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ouve o canal PROFILES_SYNC, compara timestamps (updatedAt) e atualiza
 * ou cria o perfil localmente. Agora é um simples listener e não gere a sua própria thread.
 * (Versão v3 - Usa ProfileRepository local para save)
 */
public class ProfileSyncSubscriber implements RedisMessageListener { // Implementa a interface
    private static final Logger LOGGER = Logger.getLogger(ProfileSyncSubscriber.class.getName());
    private final ProfileService profiles; // Usado APENAS para getByUuid
    private final ObjectMapper mapper = new ObjectMapper();

    // Instância do Repositório para salvar localmente sem disparar um novo publish
    private final ProfileRepository repository = new ProfileRepository();

    // Obtém o ProfileService no construtor
    public ProfileSyncSubscriber() {
        // Obter o ProfileService é necessário para a lógica de 'getByUuid'
        this.profiles = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService not found for ProfileSyncSubscriber"));
        LOGGER.info("ProfileSyncSubscriber (v3 - Local Repo Save) inicializado.");
    }

    // Não há start() ou stop() aqui (gerenciado pelo ProfileModule)

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel)) {
            return;
        }
        handle(message); // Passa para a lógica de tratamento
    }

    private void handle(String json) {
        JsonNode node = null; // Definido fora do try para uso no catch
        try {
            node = mapper.readTree(json);
            String action = node.path("action").asText("");
            String uuidStr = node.path("uuid").asText(null);
            if (uuidStr == null) {
                LOGGER.warning("ProfileSync: Received message without UUID. Discarding.");
                return;
            }
            UUID uuid = UUID.fromString(uuidStr);

            if ("delete".equals(action)) {
                // Ação 'delete' é rara, mas se acontecer, apenas invalidamos o cache de sessão.
                ServiceRegistry.getInstance().getService(RoleService.class)
                        .ifPresent(rs -> rs.invalidateSession(uuid));
                LOGGER.fine("ProfileSync: Received delete for " + uuid + ". Session invalidated.");
                return;
            }

            if ("upsert".equals(action)) {
                // --- Verificação de Timestamp (A Correção Chave) ---
                long messageUpdatedAt = node.path("updatedAt").asLong(0);
                if (messageUpdatedAt == 0) {
                    LOGGER.warning("ProfileSync: Received upsert for " + uuid + " with missing/invalid updatedAt. Discarding.");
                    return;
                }

                // Usa ProfileService para a leitura inicial (poderia usar 'repository' também)
                Optional<Profile> localProfileOpt = profiles.getByUuid(uuid);

                if (localProfileOpt.isPresent()) {
                    // Perfil EXISTE localmente
                    Profile localProfile = localProfileOpt.get();

                    // Compara timestamps
                    if (messageUpdatedAt <= localProfile.getUpdatedAt()) {
                        // Mensagem é ANTIGA ou igual. Descarta.
                        LOGGER.finer("ProfileSync: Discarded stale upsert for " + uuid + " (Msg: " + messageUpdatedAt + " <= Local: " + localProfile.getUpdatedAt() + ")");
                        return;
                    }

                    // Mensagem é MAIS NOVA. Atualiza o perfil local.
                    LOGGER.fine("ProfileSync: Applying NEWER upsert for " + uuid + " (Msg: " + messageUpdatedAt + " > Local: " + localProfile.getUpdatedAt() + ")");
                    updateProfileFromJson(localProfile, node); // Atualiza o objeto 'localProfile'

                    // Salva (mas não publica de volta)
                    saveProfileLocally(localProfile, node); // Passa node

                } else {
                    // Perfil NÃO EXISTE localmente. Cria a partir do JSON.
                    LOGGER.info("ProfileSync: Received upsert for NEW profile " + uuid + ". Creating locally.");
                    Profile newProfile = createProfileFromJson(node, uuid); // Cria novo objeto

                    // Salva (mas não publica de volta)
                    saveProfileLocally(newProfile, node); // Passa node
                }

                // --- Sincronização de Roles Pós-Atualização ---
                // Publica uma mensagem ROLE_SYNC para forçar o RoleService
                // a recarregar as permissões deste jogador em todas as instâncias.
                ServiceRegistry.getInstance().getService(RoleService.class)
                        .ifPresent(rs -> rs.publishSync(uuid));
                LOGGER.finer("ProfileSync: Triggered ROLE_SYNC for " + uuid + " after profile update.");
            }

        } catch (Exception e) {
            String jsonSnippet = (json != null && json.length() > 150) ? json.substring(0, 150) + "..." : json;
            LOGGER.log(Level.SEVERE, "ProfileSync: Error handling message: " + jsonSnippet, e);
        }
    }

    /**
     * Salva o perfil no repositório local sem disparar um novo 'publish' (pois usa 'repository' diretamente).
     */
    private void saveProfileLocally(Profile profile, JsonNode node) { // Recebe node
        try {
            long now = System.currentTimeMillis();
            if (profile.getCreatedAt() == 0L) {
                // Usa o updatedAt da mensagem como fallback para createdAt se for 0
                profile.setCreatedAt(node.path("updatedAt").asLong(now));
            }
            // Garante que o updatedAt seja o da mensagem
            profile.setUpdatedAt(node.path("updatedAt").asLong(now));

            if (profile.getId() == null) {
                JsonNode idNode = node.path("id");
                if (idNode.isIntegralNumber() && idNode.asInt() != 0) { // Verifica se é número válido
                    profile.setId(idNode.asInt());
                } else {
                    LOGGER.severe("ProfileSync: Criando perfil para " + profile.getUuid() + " mas ID está faltando/inválido no sync message! Gerando novo ID.");
                    profile.setId(MongoSequences.getNext("profiles"));
                    LOGGER.warning("ProfileSync: Atribuído novo ID sequencial " + profile.getId() + " para " + profile.getUuid() + " via sync.");
                }
            }

            repository.upsert(profile); // Salva usando a instância local do repositório

            LOGGER.fine("ProfileSync: Profile " + profile.getId() + " (UUID: " + profile.getUuid() + ") salvo localmente via sync.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ProfileSync: Falha ao salvar perfil localmente para UUID: " + profile.getUuid(), e);
        }
    }


    // Helper para atualizar campos
    private void updateProfileFromJson(Profile p, JsonNode node) {
        // Atualiza campos apenas se existirem E não forem nulos no JSON
        if (node.hasNonNull("id")) p.setId(node.get("id").asInt());
        if (node.hasNonNull("name")) p.setName(node.get("name").asText());
        if (node.hasNonNull("username")) p.setUsername(node.get("username").asText());
        p.setCash(node.path("cash").asInt(p.getCash())); // Usa path..asInt(default) para non-null
        p.setPremiumAccount(node.path("premium").asBoolean(p.isPremiumAccount()));
        if (node.hasNonNull("cashTopPosition")) p.setCashTopPosition(node.get("cashTopPosition").asInt()); else p.setCashTopPosition(null);
        if (node.hasNonNull("cashTopPositionEnteredAt")) p.setCashTopPositionEnteredAt(node.get("cashTopPositionEnteredAt").asLong()); else p.setCashTopPositionEnteredAt(null);
        if (node.hasNonNull("firstIp")) p.setFirstIp(node.get("firstIp").asText());
        if (node.hasNonNull("lastIp")) p.setLastIp(node.get("lastIp").asText());

        List<String> ipHistory = new ArrayList<>();
        if (node.hasNonNull("ipHistory") && node.get("ipHistory").isArray()) {
            for (JsonNode ipNode : node.get("ipHistory")) { ipHistory.add(ipNode.asText()); }
        }
        p.setIpHistory(ipHistory);

        p.setFirstLogin(node.path("firstLogin").asLong(p.getFirstLogin()));
        p.setLastLogin(node.path("lastLogin").asLong(p.getLastLogin()));
        if (node.hasNonNull("lastClientVersion")) p.setLastClientVersion(node.get("lastClientVersion").asText());
        if (node.hasNonNull("lastClientType")) p.setLastClientType(node.get("lastClientType").asText());
        if (node.hasNonNull("primaryRoleName")) p.setPrimaryRoleName(node.get("primaryRoleName").asText());

        List<PlayerRole> roles = new ArrayList<>();
        if (node.hasNonNull("roles") && node.get("roles").isArray()) {
            for (JsonNode roleNode : node.get("roles")) {
                try {
                    // Desserializa o PlayerRole completo do JSON
                    PlayerRole pr = PlayerRole.builder()
                            .roleName(roleNode.path("roleName").asText(null))
                            .status(PlayerRole.Status.valueOf(roleNode.path("status").asText("ACTIVE")))
                            .expiresAt(roleNode.path("expiresAt").isIntegralNumber() ? roleNode.path("expiresAt").asLong() : null)
                            .paused(roleNode.path("paused").asBoolean(false))
                            .pausedTimeRemaining(roleNode.path("pausedTimeRemaining").isIntegralNumber() ? roleNode.path("pausedTimeRemaining").asLong() : null)
                            .addedAt(roleNode.path("addedAt").asLong(0))
                            .removedAt(roleNode.path("removedAt").isIntegralNumber() ? roleNode.path("removedAt").asLong() : null)
                            .build();

                    if (pr.getRoleName() != null) {
                        roles.add(pr);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "ProfileSync: Falha ao desserializar PlayerRole node: " + roleNode.toString(), e);
                }
            }
        }
        p.setRoles(roles);

        p.setCreatedAt(node.path("createdAt").asLong(p.getCreatedAt()));
        p.setUpdatedAt(node.path("updatedAt").asLong(p.getUpdatedAt())); // Mais importante
    }

    // Helper para criar Profile
    private Profile createProfileFromJson(JsonNode node, UUID uuid) {
        Profile newProfile = new Profile();
        newProfile.setUuid(uuid);
        updateProfileFromJson(newProfile, node); // Reutiliza lógica

        if (newProfile.getId() == null || newProfile.getId() == 0) {
            if (node.hasNonNull("id") && node.get("id").isIntegralNumber() && node.get("id").asInt() != 0) {
                newProfile.setId(node.get("id").asInt());
            } else {
                LOGGER.severe("ProfileSync: Criando perfil para " + uuid + " mas ID está faltando/inválido no sync message! Gerando novo ID.");
                newProfile.setId(MongoSequences.getNext("profiles"));
            }
        }
        if (newProfile.getCreatedAt() == 0) {
            newProfile.setCreatedAt(node.path("updatedAt").asLong(System.currentTimeMillis())); // Usa updatedAt como fallback
        }
        // UpdatedAt já foi definido por updateProfileFromJson
        return newProfile;
    }
}