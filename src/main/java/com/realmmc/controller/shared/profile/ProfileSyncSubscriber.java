package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileSyncSubscriber implements RedisMessageListener {
    private static final Logger LOGGER = Logger.getLogger(ProfileSyncSubscriber.class.getName());
    private final ProfileService profiles;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ProfileRepository repository = new ProfileRepository();

    public ProfileSyncSubscriber() {
        this.profiles = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService not found for ProfileSyncSubscriber"));
        LOGGER.info("ProfileSyncSubscriber (v3 - Local Repo Save) inicializado.");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel)) {
            return;
        }
        handle(message);
    }

    private void handle(String json) {
        JsonNode node = null;
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
                ServiceRegistry.getInstance().getService(RoleService.class)
                        .ifPresent(rs -> rs.invalidateSession(uuid));
                LOGGER.fine("ProfileSync: Received delete for " + uuid + ". Session invalidated.");
                return;
            }

            if ("upsert".equals(action)) {
                long messageUpdatedAt = node.path("updatedAt").asLong(0);
                if (messageUpdatedAt == 0) {
                    LOGGER.warning("ProfileSync: Received upsert for " + uuid + " with missing/invalid updatedAt. Discarding.");
                    return;
                }

                Optional<Profile> localProfileOpt = profiles.getByUuid(uuid);

                if (localProfileOpt.isPresent()) {
                    Profile localProfile = localProfileOpt.get();

                    if (messageUpdatedAt <= localProfile.getUpdatedAt()) {
                        LOGGER.finer("ProfileSync: Discarded stale upsert for " + uuid + " (Msg: " + messageUpdatedAt + " <= Local: " + localProfile.getUpdatedAt() + ")");
                        return;
                    }

                    LOGGER.fine("ProfileSync: Applying NEWER upsert for " + uuid + " (Msg: " + messageUpdatedAt + " > Local: " + localProfile.getUpdatedAt() + ")");
                    updateProfileFromJson(localProfile, node);

                    saveProfileLocally(localProfile, node);

                } else {
                    LOGGER.info("ProfileSync: Received upsert for NEW profile " + uuid + ". Creating locally.");
                    Profile newProfile = createProfileFromJson(node, uuid);

                    saveProfileLocally(newProfile, node);
                }

                ServiceRegistry.getInstance().getService(RoleService.class)
                        .ifPresent(rs -> rs.publishSync(uuid));
                LOGGER.finer("ProfileSync: Triggered ROLE_SYNC for " + uuid + " after profile update.");
            }

        } catch (Exception e) {
            String jsonSnippet = (json != null && json.length() > 150) ? json.substring(0, 150) + "..." : json;
            LOGGER.log(Level.SEVERE, "ProfileSync: Error handling message: " + jsonSnippet, e);
        }
    }

    private void saveProfileLocally(Profile profile, JsonNode node) {
        try {
            long now = System.currentTimeMillis();
            if (profile.getCreatedAt() == 0L) {
                profile.setCreatedAt(node.path("updatedAt").asLong(now));
            }
            profile.setUpdatedAt(node.path("updatedAt").asLong(now));

            if (profile.getId() == null) {
                JsonNode idNode = node.path("id");
                if (idNode.isIntegralNumber() && idNode.asInt() != 0) {
                    profile.setId(idNode.asInt());
                } else {
                    LOGGER.severe("ProfileSync: Criando perfil para " + profile.getUuid() + " mas ID está faltando/inválido no sync message! Gerando novo ID.");
                    profile.setId(MongoSequences.getNext("profiles"));
                    LOGGER.warning("ProfileSync: Atribuído novo ID sequencial " + profile.getId() + " para " + profile.getUuid() + " via sync.");
                }
            }

            repository.upsert(profile);

            LOGGER.fine("ProfileSync: Profile " + profile.getId() + " (UUID: " + profile.getUuid() + ") salvo localmente via sync.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ProfileSync: Falha ao salvar perfil localmente para UUID: " + profile.getUuid(), e);
        }
    }


    private void updateProfileFromJson(Profile p, JsonNode node) {
        if (node.hasNonNull("id")) p.setId(node.get("id").asInt());
        if (node.hasNonNull("name")) p.setName(node.get("name").asText());
        if (node.hasNonNull("username")) p.setUsername(node.get("username").asText());
        p.setCash(node.path("cash").asInt(p.getCash()));
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
        p.setUpdatedAt(node.path("updatedAt").asLong(p.getUpdatedAt()));
    }

    private Profile createProfileFromJson(JsonNode node, UUID uuid) {
        Profile newProfile = new Profile();
        newProfile.setUuid(uuid);
        updateProfileFromJson(newProfile, node);

        if (newProfile.getId() == null || newProfile.getId() == 0) {
            if (node.hasNonNull("id") && node.get("id").isIntegralNumber() && node.get("id").asInt() != 0) {
                newProfile.setId(node.get("id").asInt());
            } else {
                LOGGER.severe("ProfileSync: Criando perfil para " + uuid + " mas ID está faltando/inválido no sync message! Gerando novo ID.");
                newProfile.setId(MongoSequences.getNext("profiles"));
            }
        }
        if (newProfile.getCreatedAt() == 0) {
            newProfile.setCreatedAt(node.path("updatedAt").asLong(System.currentTimeMillis()));
        }
        return newProfile;
    }
}