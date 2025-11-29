package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.messaging.Messages;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileService {

    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());
    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();

    private Optional<StatisticsService> getStatsService() {
        return Optional.ofNullable(ServiceRegistry.getInstance().getService(StatisticsService.class).orElse(null));
    }

    private Optional<PreferencesService> getPreferencesService() {
        return Optional.ofNullable(ServiceRegistry.getInstance().getService(PreferencesService.class).orElse(null));
    }

    private Optional<SessionTrackerService> getSessionTracker() {
        return ServiceRegistry.getInstance().getService(SessionTrackerService.class);
    }

    public Optional<Profile> getByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            Optional<Profile> profileOpt = repository.findByUuid(uuid);
            if (profileOpt.isEmpty()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                profileOpt = repository.findByUuid(uuid);
            }
            return profileOpt;
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching profile by UUID: " + uuid, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error fetching profile by UUID: " + uuid, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getById(int id) {
        try {
            return repository.findById(id);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching profile by ID: " + id, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error fetching profile by ID: " + id, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            return repository.findByName(name);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching profile by name: " + name, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error fetching profile by name: " + name, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try {
            return repository.findByUsername(username.toLowerCase());
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching profile by username: " + username, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error fetching profile by username: " + username, e);
            return Optional.empty();
        }
    }

    public List<Profile> findByActiveRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return repository.findByActiveRoleName(roleName);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching profiles by active role: " + roleName, e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error fetching profiles by active role: " + roleName, e);
            return Collections.emptyList();
        }
    }

    public void save(Profile profile) {
        Objects.requireNonNull(profile, "Profile cannot be null for saving.");
        long now = System.currentTimeMillis();

        if (profile.getCreatedAt() == 0L) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);

        try {
            if (profile.getId() == null) {
                profile.setId(MongoSequences.getNext("profiles"));
                LOGGER.info("[ProfileService] Assigned new sequential ID (" + profile.getId() + ") for profile UUID: " + profile.getUuid());
            }

            repository.upsert(profile);
            publish("upsert", profile);
            updateSessionData(profile.getUuid(), profile.getCash(), profile.getPrimaryRoleName(), profile.getEquippedMedal());

            LOGGER.log(Level.INFO, "[ProfileService] Profile {0} (UUID: {1}) saved/updated successfully. ID: {2}",
                    new Object[]{profile.getName(), profile.getUuid(), profile.getId()});

        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error saving profile for UUID: " + profile.getUuid(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error saving profile for UUID: " + profile.getUuid(), e);
            throw new RuntimeException("Unexpected failure saving profile", e);
        }
    }

    private void updateSessionData(UUID uuid, int cash, String primaryRole, String medal) {
        getSessionTracker().ifPresent(session -> {
            try {
                session.setSessionField(uuid, "cash", String.valueOf(cash));
                if (primaryRole != null) session.setSessionField(uuid, "role", primaryRole);
                if (medal != null) session.setSessionField(uuid, "medal", medal);
            } catch (Exception e) {
                LOGGER.warning("[ProfileService] Failed to update session data in Redis for " + uuid);
            }
        });
    }

    public void delete(UUID uuid) {
        if (uuid == null) return;
        try {
            Optional<Profile> profileOpt = getByUuid(uuid);
            repository.deleteByUuid(uuid);
            LOGGER.info("[ProfileService] Profile deleted for UUID: " + uuid);

            Profile dummy = new Profile();
            dummy.setUuid(uuid);
            profileOpt.ifPresent(p -> dummy.setId(p.getId()));
            publish("delete", dummy);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error deleting profile for UUID: " + uuid, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error deleting profile for UUID: " + uuid, e);
        }
    }

    public boolean exists(UUID uuid) {
        if (uuid == null) return false;
        try {
            return repository.collection().countDocuments(Filters.eq("uuid", uuid)) > 0;
        } catch (MongoException e) {
            LOGGER.log(Level.WARNING, "[ProfileService] MongoDB error checking profile existence for UUID: {0}", uuid);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ProfileService] Unexpected error checking profile existence for UUID: {0}", uuid);
            return false;
        }
    }

    public Profile ensureProfile(UUID loginUuid, String displayName, String username, String currentIp,
                                 String clientVersion, String clientType, boolean isPremium, Object playerObject) {

        Objects.requireNonNull(loginUuid, "loginUuid cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        final String usernameLower = username.toLowerCase();
        final AtomicBoolean needsSave = new AtomicBoolean(false);
        Profile profileToReturn;

        Optional<Profile> profileOptByUuid = getByUuid(loginUuid);

        if (profileOptByUuid.isPresent()) {
            profileToReturn = profileOptByUuid.get();

            if (profileToReturn.getFirstClientType() == null || profileToReturn.getFirstClientType().isEmpty()) {
                profileToReturn.setFirstClientType(clientType);
                needsSave.set(true);
            }
            if (profileToReturn.getFirstClientVersion() == null || profileToReturn.getFirstClientVersion().isEmpty()) {
                profileToReturn.setFirstClientVersion(clientVersion);
                needsSave.set(true);
            }

            if (!displayName.equals(profileToReturn.getName())) {
                profileToReturn.setName(displayName);
                profileToReturn.setUsername(usernameLower);
                needsSave.set(true);
            } else if (!usernameLower.equals(profileToReturn.getUsername())) {
                profileToReturn.setUsername(usernameLower);
                needsSave.set(true);
            }

            if (currentIp != null && !currentIp.isEmpty()) {
                if (!currentIp.equals(profileToReturn.getLastIp())) {
                    profileToReturn.setLastIp(currentIp);
                    needsSave.set(true);
                }
                if (profileToReturn.getIpHistory() == null) profileToReturn.setIpHistory(new ArrayList<>());
                if (!profileToReturn.getIpHistory().contains(currentIp)) {
                    profileToReturn.getIpHistory().add(currentIp);
                    needsSave.set(true);
                }
            }

            if (profileToReturn.isPremiumAccount() != isPremium) {
                profileToReturn.setPremiumAccount(isPremium);
                needsSave.set(true);
            }

            if (profileToReturn.getEquippedMedal() == null) {
                profileToReturn.setEquippedMedal("none");
                needsSave.set(true);
            }

            profileToReturn.setLastLogin(System.currentTimeMillis());
            profileToReturn.setLastClientVersion(clientVersion);
            profileToReturn.setLastClientType(clientType);
            needsSave.set(true);

            if (profileToReturn.getRoles() == null) profileToReturn.setRoles(new ArrayList<>());
            boolean hasDefault = profileToReturn.getRoles().stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()));
            if (!hasDefault) {
                profileToReturn.getRoles().add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
                needsSave.set(true);
            }

            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> {
                prefs.ensurePreferences(finalProfileForServices, null);
            });

        } else {
            long now = System.currentTimeMillis();
            int profileId = MongoSequences.getNext("profiles");
            final Language initialLang = Messages.determineInitialLanguage(playerObject);

            profileToReturn = Profile.builder()
                    .id(profileId)
                    .uuid(loginUuid)
                    .name(displayName)
                    .username(usernameLower)
                    .firstIp(currentIp)
                    .lastIp(currentIp)
                    .ipHistory(new ArrayList<>(currentIp != null ? List.of(currentIp) : List.of()))
                    .firstLogin(now)
                    .lastLogin(now)
                    .firstClientVersion(clientVersion)
                    .firstClientType(clientType)
                    .lastClientVersion(clientVersion)
                    .lastClientType(clientType)
                    .cash(0)
                    .pendingCash(0)
                    .roles(new ArrayList<>(List.of(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build())))
                    .primaryRoleName("default")
                    .premiumAccount(isPremium)
                    .equippedMedal("none")
                    .createdAt(now)
                    .build();

            needsSave.set(true);
            LOGGER.log(Level.INFO, "[ProfileService] Creating new profile ID {0} for {1} ({2})",
                    new Object[]{profileId, displayName, loginUuid});

            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(finalProfileForServices, initialLang));
        }

        if (needsSave.get()) {
            try {
                save(profileToReturn);
            } catch (MongoException e) {
                if (e.getCode() == 11000 && e.getMessage() != null && (e.getMessage().contains("index: username_1") || e.getMessage().contains("index: uuid_1"))) {
                    LOGGER.log(Level.WARNING, "[ProfileService] Duplicate key error saving profile for {0}. Attempting reload.", usernameLower);
                    profileToReturn = getByUuid(loginUuid)
                            .orElseThrow(() -> new RuntimeException("Failed to reload profile after duplicate key error", e));
                } else {
                    LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error saving profile during ensureProfile", e);
                    throw e;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error saving profile during ensureProfile", e);
                throw new RuntimeException("Unexpected failure saving profile", e);
            }
        }

        final Profile finalProfileToReturn = profileToReturn;
        getPreferencesService().ifPresent(prefs -> {
            prefs.loadAndCachePreferences(finalProfileToReturn.getUuid());
        });

        return finalProfileToReturn;
    }

    public void updateName(UUID uuid, String newName) {
        if (uuid == null || newName == null || newName.isEmpty()) return;
        update(uuid, p -> {
            if (!newName.equals(p.getName())) {
                p.setName(newName);
                p.setUsername(newName.toLowerCase());
                getStatsService().ifPresent(stats -> stats.updateIdentification(p));
                getPreferencesService().ifPresent(prefs -> prefs.updateIdentification(p));
                return true;
            }
            return false;
        }, "update_name");
    }

    public void setUsername(UUID uuid, String username) {
        if (uuid == null || username == null || username.isEmpty()) return;
        final String usernameLower = username.toLowerCase();
        update(uuid, p -> {
            if (!usernameLower.equals(p.getUsername())) {
                p.setUsername(usernameLower);
                return true;
            }
            return false;
        }, "set_username");
    }

    public void addCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount <= 0) return;

        org.bson.conversions.Bson filter = Filters.eq("uuid", targetUuid);
        org.bson.conversions.Bson update = Updates.combine(
                Updates.inc("cash", amount),
                Updates.inc("pendingCash", amount),
                Updates.set("updatedAt", System.currentTimeMillis())
        );

        UpdateResult result = repository.collection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            try {
                ObjectNode node = mapper.createObjectNode();
                node.put("uuid", targetUuid.toString());
                node.put("amount", amount);
                RedisPublisher.publish(RedisChannel.CASH_NOTIFICATION, node.toString());
            } catch (Exception e) {
                LOGGER.warning("Failed to publish cash notification for " + targetUuid);
            }

            updateLocalAndPublish(targetUuid, p -> {
                p.setCash(p.getCash() + amount);
                p.setPendingCash(p.getPendingCash() + amount);
            });
        } else {
            LOGGER.warning("Tentativa de adicionar cash a UUID inexistente: " + targetUuid);
        }
    }

    public void resetPendingCash(UUID targetUuid) {
        if (targetUuid == null) return;
        update(targetUuid, p -> {
            if (p.getPendingCash() > 0) {
                p.setPendingCash(0);
                return true;
            }
            return false;
        }, "reset_pending_cash");
    }

    public boolean removeCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount <= 0) return false;

        long now = System.currentTimeMillis();
        org.bson.conversions.Bson filter = Filters.and(
                Filters.eq("uuid", targetUuid),
                Filters.gte("cash", amount)
        );
        org.bson.conversions.Bson update = Updates.combine(
                Updates.inc("cash", -amount),
                Updates.set("updatedAt", now)
        );

        UpdateResult result = repository.collection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            updateLocalAndPublish(targetUuid, p -> p.setCash(p.getCash() - amount));
            return true;
        }

        return false;
    }

    public void setCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount < 0) return;

        update(targetUuid, p -> {
            if (p.getCash() != amount) {
                p.setCash(amount);
                return true;
            }
            return false;
        }, "cash_set");
    }

    public void clearCash(UUID targetUuid, UUID sourceUuid, String sourceName) {
        if (targetUuid == null) return;
        update(targetUuid, p -> {
            p.setCash(0);
            return true;
        }, "cash_clear");
    }

    private void updateLocalAndPublish(UUID uuid, Consumer<Profile> action) {
        Optional<Profile> localOpt = getByUuid(uuid);
        if (localOpt.isPresent()) {
            Profile p = localOpt.get();
            action.accept(p);
            p.setUpdatedAt(System.currentTimeMillis());

            publish("upsert", p);
            updateSessionData(uuid, p.getCash(), p.getPrimaryRoleName(), p.getEquippedMedal());
        }
    }

    private void update(UUID uuid, ProfileModifier modifier, String actionContext) {
        if (uuid == null || modifier == null) return;
        Optional<Profile> profileOpt = getByUuid(uuid);
        profileOpt.ifPresentOrElse(
                profile -> {
                    boolean changed = false;
                    try {
                        changed = modifier.modify(profile);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "[ProfileService] Error during modification ('" + actionContext + "') for UUID: " + uuid, e);
                        return;
                    }

                    if (changed) {
                        try {
                            save(profile);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "[ProfileService] Error saving profile after '" + actionContext + "' for UUID: " + uuid, e);
                        }
                    }
                },
                () -> {
                    LOGGER.warning("[ProfileService] Attempt to '" + actionContext + "' failed: Profile not found for UUID: " + uuid);
                }
        );
    }

    @FunctionalInterface
    private interface ProfileModifier {
        boolean modify(Profile profile);
    }

    private void publish(String action, Profile profile) {
        if (profile == null || profile.getUuid() == null || action == null) {
            return;
        }

        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());

            if ("upsert".equals(action)) {
                if (profile.getId() != null) node.put("id", profile.getId()); else node.putNull("id");
                node.put("name", profile.getName());
                node.put("username", profile.getUsername());
                node.put("cash", profile.getCash());
                node.put("premium", profile.isPremiumAccount());
                node.put("equippedMedal", profile.getEquippedMedal());
                if (profile.getCashTopPosition() != null) node.put("cashTopPosition", profile.getCashTopPosition()); else node.putNull("cashTopPosition");
                if (profile.getCashTopPositionEnteredAt() != null) node.put("cashTopPositionEnteredAt", profile.getCashTopPositionEnteredAt()); else node.putNull("cashTopPositionEnteredAt");
                node.put("firstIp", profile.getFirstIp());
                node.put("lastIp", profile.getLastIp());
                if (profile.getIpHistory() != null) { ArrayNode ipHistoryNode = node.putArray("ipHistory"); profile.getIpHistory().forEach(ipHistoryNode::add); } else { node.putArray("ipHistory"); }
                node.put("firstLogin", profile.getFirstLogin());
                node.put("lastLogin", profile.getLastLogin());

                node.put("firstClientVersion", profile.getFirstClientVersion());
                node.put("firstClientType", profile.getFirstClientType());
                node.put("lastClientVersion", profile.getLastClientVersion());
                node.put("lastClientType", profile.getLastClientType());

                node.put("primaryRoleName", profile.getPrimaryRoleName());

                List<PlayerRole> roles = profile.getRoles();
                if (roles != null) {
                    ArrayNode rolesNode = node.putArray("roles");
                    for (PlayerRole pr : roles) {
                        if (pr == null || pr.getRoleName() == null) continue;
                        ObjectNode roleInfo = rolesNode.addObject();
                        roleInfo.put("roleName", pr.getRoleName());
                        roleInfo.put("status", pr.getStatus() != null ? pr.getStatus().name() : PlayerRole.Status.ACTIVE.name());
                        if (pr.getExpiresAt() != null) roleInfo.put("expiresAt", pr.getExpiresAt()); else roleInfo.putNull("expiresAt");
                        roleInfo.put("paused", pr.isPaused());
                        if (pr.getPausedTimeRemaining() != null) roleInfo.put("pausedTimeRemaining", pr.getPausedTimeRemaining()); else roleInfo.putNull("pausedTimeRemaining");
                        roleInfo.put("addedAt", pr.getAddedAt());
                        if (pr.getRemovedAt() != null) roleInfo.put("removedAt", pr.getRemovedAt()); else roleInfo.putNull("removedAt");
                    }
                } else {
                    node.putArray("roles");
                }

                node.put("createdAt", profile.getCreatedAt());
                node.put("updatedAt", profile.getUpdatedAt());

            } else if ("delete".equals(action)) {
                if (profile.getId() != null) node.put("id", profile.getId());
            }

            String jsonMessage = node.toString();
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, jsonMessage);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Failed to publish profile sync message for UUID: " + profile.getUuid(), e);
        }
    }

    public List<Profile> getTopCash(int limit) {
        try {
            return repository.findTopByCash(limit);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching top cash", e);
            return Collections.emptyList();
        }
    }

    public long getPlayerRank(int playerCash) {
        try {
            return repository.countByCashGreaterThan(playerCash) + 1;
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] MongoDB error fetching player cash rank", e);
            return -1;
        }
    }
}