package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.messaging.Messages;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public Optional<Profile> getByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            Optional<Profile> profileOpt = repository.findByUuid(uuid);
            if (profileOpt.isEmpty()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                profileOpt = repository.findByUuid(uuid);
                if (profileOpt.isEmpty()) {
                    LOGGER.log(Level.FINER, "[ProfileService:Search] Profile not found in DB for {0} (even after retry)", uuid);
                }
            }
            return profileOpt;
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] MongoDB error fetching profile by UUID: " + uuid, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] Unexpected error fetching profile by UUID: " + uuid, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getById(int id) {
        try {
            return repository.findById(id);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] MongoDB error fetching profile by ID: " + id, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] Unexpected error fetching profile by ID: " + id, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            return repository.findByName(name);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] MongoDB error fetching profile by name: " + name, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] Unexpected error fetching profile by name: " + name, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try {
            return repository.findByUsername(username.toLowerCase());
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] MongoDB error fetching profile by username: " + username, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] Unexpected error fetching profile by username: " + username, e);
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
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] MongoDB error fetching profiles by active role: " + roleName, e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Search] Unexpected error fetching profiles by active role: " + roleName, e);
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
                LOGGER.info("[ProfileService:Save] Assigned new sequential ID (" + profile.getId() + ") for profile UUID: " + profile.getUuid());
            }

            LOGGER.log(Level.FINE, "[ProfileService:Save] Attempting to save profile ID: {0}, UUID: {1}", new Object[]{profile.getId(), profile.getUuid()});

            repository.upsert(profile);
            publish("upsert", profile);
            LOGGER.log(Level.INFO, "[ProfileService:Save] Profile {0} (UUID: {1}) saved/updated successfully. ID: {2}",
                    new Object[]{profile.getName(), profile.getUuid(), profile.getId()});

        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Save] MongoDB error saving (upsert) profile for UUID: " + profile.getUuid() + ", ID: " + profile.getId(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Save] Unexpected error saving (upsert) profile for UUID: " + profile.getUuid() + ", ID: " + profile.getId(), e);
            throw new RuntimeException("Unexpected failure saving profile", e);
        }
    }

    public void delete(UUID uuid) {
        if (uuid == null) return;
        try {
            Optional<Profile> profileOpt = getByUuid(uuid);
            repository.deleteByUuid(uuid);
            LOGGER.info("[ProfileService:Delete] Profile deleted (if existed) for UUID: " + uuid);

            Profile dummy = new Profile();
            dummy.setUuid(uuid);
            profileOpt.ifPresent(p -> dummy.setId(p.getId()));
            publish("delete", dummy);
        } catch (MongoException e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Delete] MongoDB error deleting profile for UUID: " + uuid, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Delete] Unexpected error deleting profile for UUID: " + uuid, e);
        }
    }

    public boolean exists(UUID uuid) {
        if (uuid == null) return false;
        try {
            return repository.collection().countDocuments(Filters.eq("uuid", uuid)) > 0;
        } catch (MongoException e) {
            LOGGER.log(Level.WARNING, "[ProfileService:Exists] MongoDB error checking profile existence for UUID: {0}", uuid);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ProfileService:Exists] Unexpected error checking profile existence for UUID: {0}", uuid);
            return false;
        }
    }

    public Profile ensureProfile(UUID loginUuid, String displayName, String username, String currentIp,
                                 String clientVersion, String clientType, boolean isPremium, Object playerObject) {

        Objects.requireNonNull(loginUuid, "loginUuid cannot be null for ensureProfile");
        Objects.requireNonNull(displayName, "displayName cannot be null for ensureProfile");
        Objects.requireNonNull(username, "username cannot be null for ensureProfile");
        final String usernameLower = username.toLowerCase();
        final AtomicBoolean needsSave = new AtomicBoolean(false);
        Profile profileToReturn;

        Optional<Profile> profileOptByUuid = getByUuid(loginUuid);

        if (profileOptByUuid.isPresent()) {
            profileToReturn = profileOptByUuid.get();
            LOGGER.finest("[ProfileService:Ensure] Profile found via login UUID: " + loginUuid + " for username: " + usernameLower);

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
                LOGGER.log(Level.FINER, "[ProfileService:Ensure] Updating name/username for {0} -> {1} ({2})", new Object[]{profileToReturn.getUuid(), displayName, usernameLower});
            }
            else if (!usernameLower.equals(profileToReturn.getUsername())) {
                profileToReturn.setUsername(usernameLower);
                needsSave.set(true);
                LOGGER.log(Level.FINER, "[ProfileService:Ensure] Updating username (no name change) for {0} -> {1}", new Object[]{profileToReturn.getUuid(), usernameLower});
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
                LOGGER.log(Level.INFO, "[ProfileService:Ensure] Premium status updated to {0} on existing profile of {1}", new Object[]{isPremium, usernameLower});
            }

            profileToReturn.setLastLogin(System.currentTimeMillis());
            profileToReturn.setLastClientVersion(clientVersion);
            profileToReturn.setLastClientType(clientType);
            needsSave.set(true);

            final UUID profileUuidFinal = profileToReturn.getUuid();
            if (profileToReturn.getRoles() == null) profileToReturn.setRoles(new ArrayList<>());
            boolean hasDefault = profileToReturn.getRoles().stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()));
            if (!hasDefault) {
                profileToReturn.getRoles().add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
                needsSave.set(true);
                LOGGER.log(Level.WARNING, "[ProfileService:Ensure] Existing profile (UUID: {0}) found without default group. Adding it.", profileUuidFinal);
            }

            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> {
                prefs.ensurePreferences(finalProfileForServices, null);
            });

        } else {
            long now = System.currentTimeMillis();
            int profileId = MongoSequences.getNext("profiles");
            Language initialLang = Messages.determineInitialLanguage(playerObject);

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
                    .roles(new ArrayList<>(List.of(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build())))
                    .primaryRoleName("default")
                    .premiumAccount(isPremium)
                    .createdAt(now)
                    .build();

            needsSave.set(true);
            LOGGER.log(Level.INFO, "[ProfileService:Ensure] Creating new profile ID {0} for {1} ({2}) with initial language {3}",
                    new Object[]{profileId, displayName, loginUuid, initialLang});

            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(finalProfileForServices, initialLang));
        }

        final Profile profileToSaveOrCheck = profileToReturn;
        if (needsSave.get()) {
            try {
                save(profileToSaveOrCheck);
            } catch (MongoException e) {
                if (e.getCode() == 11000 && e.getMessage() != null && (e.getMessage().contains("index: username_1") || e.getMessage().contains("index: uuid_1"))) {
                    LOGGER.log(Level.WARNING, "[ProfileService:Ensure] Duplicate key error (UUID or Username) saving profile for {0} (UUID: {1}). Attempting reload.", new Object[]{usernameLower, loginUuid});
                    profileToReturn = getByUuid(loginUuid)
                            .orElseThrow(() -> new RuntimeException("Failed to reload profile after duplicate key error for UUID: " + loginUuid, e));
                } else {
                    LOGGER.log(Level.SEVERE, "[ProfileService:Ensure] CRITICAL MongoDB error saving profile during ensureProfile for " + usernameLower + " (UUID: " + loginUuid + ")", e);
                    throw e;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[ProfileService:Ensure] CRITICAL unexpected error saving profile during ensureProfile for " + usernameLower + " (UUID: " + loginUuid + ")", e);
                throw new RuntimeException("Unexpected failure saving profile", e);
            }
        } else {
            LOGGER.finest("[ProfileService:Ensure] No save changes required for UUID: " + loginUuid);
        }

        final Profile finalProfileToReturn = profileToReturn;
        getPreferencesService().ifPresent(prefs -> {
            prefs.loadAndCacheLanguage(finalProfileToReturn.getUuid());
            LOGGER.finest("[ProfileService:Ensure] Language cache loaded/updated for " + finalProfileToReturn.getUuid());
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
    public void incrementCash(UUID uuid, int delta) {
        if (uuid == null || delta == 0) return;
        update(uuid, p -> {
            long currentCash = p.getCash();
            long newCashLong = Math.max(0L, currentCash + delta);
            int finalCash = (int) Math.min(Integer.MAX_VALUE, newCashLong);
            if (p.getCash() != finalCash) {
                p.setCash(finalCash);
                return true;
            }
            return false;
        }, "cash_increment");
    }
    public void setCash(UUID uuid, int amount) {
        if (uuid == null) return;
        int finalAmount = Math.max(0, amount);
        update(uuid, p -> {
            if (p.getCash() != finalAmount) {
                p.setCash(finalAmount);
                return true;
            }
            return false;
        }, "cash_set");
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
                        LOGGER.log(Level.SEVERE, "[ProfileService:Update] Error during modification ('" + actionContext + "') for UUID: " + uuid, e);
                        return;
                    }

                    if (changed) {
                        try {
                            save(profile);
                            LOGGER.fine("[ProfileService:Update] Profile updated via '" + actionContext + "' for UUID: " + uuid);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "[ProfileService:Update] Error saving profile after '" + actionContext + "' for UUID: " + uuid, e);
                        }
                    } else {
                        LOGGER.finest("[ProfileService:Update] No modification needed ('" + actionContext + "') for UUID: " + uuid);
                    }
                },
                () -> {
                    LOGGER.warning("[ProfileService:Update] Attempt to '" + actionContext + "' failed: Profile not found for UUID: " + uuid);
                }
        );
    }

    @FunctionalInterface
    private interface ProfileModifier {
        boolean modify(Profile profile);
    }


    private void publish(String action, Profile profile) {
        if (profile == null || profile.getUuid() == null || action == null) {
            UUID profileUuid = (profile != null) ? profile.getUuid() : null;
            LOGGER.warning("[ProfileService:Publish] Attempt to publish sync for invalid profile. UUID: " + profileUuid);
            return;
        }

        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());

            if ("upsert".equals(action)) {
                if(profile.getId() != null) node.put("id", profile.getId()); else node.putNull("id");
                node.put("name", profile.getName());
                node.put("username", profile.getUsername());
                node.put("cash", profile.getCash());
                node.put("premium", profile.isPremiumAccount());
                if(profile.getCashTopPosition() != null) node.put("cashTopPosition", profile.getCashTopPosition()); else node.putNull("cashTopPosition");
                if(profile.getCashTopPositionEnteredAt() != null) node.put("cashTopPositionEnteredAt", profile.getCashTopPositionEnteredAt()); else node.putNull("cashTopPositionEnteredAt");
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
            LOGGER.log(Level.FINER, "[ProfileService:Publish] Profile sync message ('{0}') published for {1}", new Object[]{action, profile.getUuid()});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService:Publish] Failed to serialize/publish profile sync message (action=" + action + ") for UUID: " + profile.getUuid(), e);
        }
    }

    public void invalidateProfileCache(UUID uuid) {
        if (uuid != null) {
            LOGGER.finest("[ProfileService:Cache] Invalidation requested for Profile UUID: " + uuid + " (no local cache in ProfileService)");
        }
    }
}