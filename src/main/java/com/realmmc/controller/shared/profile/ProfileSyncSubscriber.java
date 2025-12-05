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
                .orElseThrow(() -> new IllegalStateException("ProfileService not found"));
        LOGGER.info("ProfileSyncSubscriber (v3 - Local Repo Save) inicializado.");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel)) return;
        handle(message);
    }

    private void handle(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String action = node.path("action").asText("");
            String uuidStr = node.path("uuid").asText(null);
            if (uuidStr == null) return;
            UUID uuid = UUID.fromString(uuidStr);

            if ("delete".equals(action)) {
                ServiceRegistry.getInstance().getService(RoleService.class).ifPresent(rs -> rs.invalidateSession(uuid));
                return;
            }

            if ("upsert".equals(action)) {
                long messageUpdatedAt = node.path("updatedAt").asLong(0);
                Optional<Profile> localProfileOpt = profiles.getByUuid(uuid);

                if (localProfileOpt.isPresent()) {
                    Profile localProfile = localProfileOpt.get();
                    if (messageUpdatedAt <= localProfile.getUpdatedAt()) return;

                    updateProfileFromJson(localProfile, node);
                    saveProfileLocally(localProfile, node);
                } else {
                    Profile newProfile = createProfileFromJson(node, uuid);
                    saveProfileLocally(newProfile, node);
                }

                ServiceRegistry.getInstance().getService(RoleService.class).ifPresent(rs -> rs.publishSync(uuid));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ProfileSync: Error handling message", e);
        }
    }

    private void saveProfileLocally(Profile profile, JsonNode node) {
        try {
            if (profile.getId() == null) {
                JsonNode idNode = node.path("id");
                if (idNode.isIntegralNumber() && idNode.asInt() != 0) {
                    profile.setId(idNode.asInt());
                } else {
                    profile.setId(MongoSequences.getNext("profiles"));
                }
            }
            repository.upsert(profile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ProfileSync: Falha ao salvar perfil localmente", e);
        }
    }

    private void updateProfileFromJson(Profile p, JsonNode node) {
        if (node.hasNonNull("id")) p.setId(node.get("id").asInt());
        if (node.hasNonNull("name")) p.setName(node.get("name").asText());
        if (node.hasNonNull("username")) p.setUsername(node.get("username").asText());

        p.setCash(node.path("cash").asInt(p.getCash()));
        p.setPendingCash(node.path("pendingCash").asInt(p.getPendingCash()));

        p.setPremiumAccount(node.path("premium").asBoolean(p.isPremiumAccount()));
        if (node.hasNonNull("equippedMedal")) p.setEquippedMedal(node.get("equippedMedal").asText());

        if (node.hasNonNull("passwordHash")) p.setPasswordHash(node.get("passwordHash").asText());
        if (node.hasNonNull("passwordSalt")) p.setPasswordSalt(node.get("passwordSalt").asText());
        if (node.hasNonNull("lastAuthorizedIp")) p.setLastAuthorizedIp(node.get("lastAuthorizedIp").asText());

        if (node.hasNonNull("firstIp")) p.setFirstIp(node.get("firstIp").asText());
        if (node.hasNonNull("lastIp")) p.setLastIp(node.get("lastIp").asText());

        List<String> ipHistory = new ArrayList<>();
        if (node.hasNonNull("ipHistory") && node.get("ipHistory").isArray()) {
            for (JsonNode ipNode : node.get("ipHistory")) { ipHistory.add(ipNode.asText()); }
        }
        p.setIpHistory(ipHistory);

        p.setFirstLogin(node.path("firstLogin").asLong(p.getFirstLogin()));
        p.setLastLogin(node.path("lastLogin").asLong(p.getLastLogin()));
        p.setLastLogout(node.path("lastLogout").asLong(p.getLastLogout()));

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
                            .pendingNotification(roleNode.path("pendingNotification").asBoolean(false))
                            .build();

                    if (pr.getRoleName() != null) {
                        roles.add(pr);
                    }
                } catch (Exception ignored) {}
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
        if (newProfile.getCreatedAt() == 0) {
            newProfile.setCreatedAt(node.path("updatedAt").asLong(System.currentTimeMillis()));
        }
        return newProfile;
    }
}