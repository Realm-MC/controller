package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.storage.mongodb.MongoSequences;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.messaging.Messages;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileService {

    private final ProfileRepository repository = new ProfileRepository();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());

    private Optional<StatisticsService> getStatsService() {
        return ServiceRegistry.getInstance().getService(StatisticsService.class);
    }

    private Optional<PreferencesService> getPreferencesService() {
        return ServiceRegistry.getInstance().getService(PreferencesService.class);
    }

    public Optional<Profile> getByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public Optional<Profile> getById(int id) {
        return repository.findById(id);
    }

    public Optional<Profile> getByName(String name) {
        return repository.findByName(name);
    }

    public Optional<Profile> getByUsername(String username) {
        return repository.findByUsername(username);
    }

    public void save(Profile profile) {
        long now = System.currentTimeMillis();
        if (profile.getCreatedAt() == 0L) profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        repository.upsert(profile);
        publish("upsert", profile);
    }

    public void delete(UUID uuid) {
        repository.findByUuid(uuid).ifPresent(p -> {
            repository.deleteByUuid(uuid);
            publish("delete", p);
        });
    }

    public boolean exists(UUID uuid) {
        return repository.findByUuid(uuid).isPresent();
    }

    public Profile ensureProfile(UUID uuid, String displayName, String username, String firstIp, String clientVersion, String clientType, boolean isPremium, Object playerObject) {
        String usernameLower = username.toLowerCase();
        Profile profile = repository.findByUuid(uuid).orElseGet(() -> {
            long now = System.currentTimeMillis();
            claimUsername(uuid, usernameLower);
            claimName(uuid, displayName);

            Language initialLang = Messages.determineInitialLanguage(playerObject);

            Profile newProfile = Profile.builder()
                    .id(MongoSequences.getNext("profiles"))
                    .uuid(uuid)
                    .name(displayName)
                    .username(usernameLower)
                    .firstIp(firstIp)
                    .lastIp(firstIp)
                    .ipHistory(new ArrayList<>(firstIp != null ? List.of(firstIp) : List.of()))
                    .firstLogin(now)
                    .lastLogin(now)
                    .lastClientVersion(clientVersion)
                    .lastClientType(clientType)
                    .cash(0)
                    .premiumAccount(isPremium)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            save(newProfile);
            LOGGER.log(Level.INFO, "Created new profile for {0} ({1}) with initial language {2}", new Object[]{displayName, uuid, initialLang});

            getStatsService().ifPresent(stats -> stats.ensureStatistics(newProfile));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(newProfile, initialLang));

            return newProfile;
        });

        getStatsService().ifPresent(stats -> stats.ensureStatistics(profile));
        getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(profile));

        boolean needsSave = false;
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(profile.getName())) {
            claimName(uuid, displayName);
            profile.setName(displayName);
            needsSave = true;
            LOGGER.log(Level.INFO, "Updating display name for {0} to {1}", new Object[]{uuid, displayName});
        }
        if (usernameLower != null && !usernameLower.isEmpty() && !usernameLower.equals(profile.getUsername())) {
            claimUsername(uuid, usernameLower);
            profile.setUsername(usernameLower);
            needsSave = true;
            LOGGER.log(Level.INFO, "Updating username for {0} to {1}", new Object[]{uuid, usernameLower});
        }

        if (profile.getIpHistory() == null) profile.setIpHistory(new ArrayList<>());
        if (firstIp != null && !firstIp.isEmpty() && !profile.getIpHistory().contains(firstIp)) {
            profile.getIpHistory().add(firstIp);
            needsSave = true;
        }

        if (profile.isPremiumAccount() != isPremium) {
            profile.setPremiumAccount(isPremium);
            needsSave = true;
        }

        profile.setLastLogin(System.currentTimeMillis());
        profile.setLastIp(firstIp);
        profile.setLastClientVersion(clientVersion);
        profile.setLastClientType(clientType);

        getStatsService().ifPresent(stats -> stats.updateIdentification(profile));
        getPreferencesService().ifPresent(prefs -> prefs.updateIdentification(profile));

        if (needsSave) save(profile);

        getPreferencesService().ifPresent(prefs -> prefs.loadAndCacheLanguage(uuid));

        return profile;
    }

    public void updateName(UUID uuid, String newName) {
        if (newName == null || newName.isEmpty()) return;
        update(uuid, p -> {
            if (!newName.equals(p.getName())) {
                claimName(uuid, newName);
                p.setName(newName);
                getStatsService().ifPresent(stats -> stats.updateIdentification(p));
                getPreferencesService().ifPresent(prefs -> prefs.updateIdentification(p));
            }
        }, "update_name");
    }

    public void setUsername(UUID uuid, String username) {
        if (username == null || username.isEmpty()) return;
        update(uuid, p -> {
            if (!username.equals(p.getUsername())) {
                claimUsername(uuid, username);
                p.setUsername(username);
            }
        }, "set_username");
    }

    public void incrementCash(UUID uuid, long delta) {
        if (delta == 0) return;
        update(uuid, p -> {
            long cur = p.getCash();
            long newCash = Math.max(0L, cur + delta);
            p.setCash((int) Math.min(Integer.MAX_VALUE, newCash));
        }, "cash_increment");
    }

    public void setCash(UUID uuid, long amount) {
        long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
        update(uuid, p -> p.setCash((int) clamped), "cash_set");
    }

    private void claimUsername(UUID ownerId, String usernameLower) {
        if (usernameLower == null || usernameLower.isEmpty()) return;
        repository.findByUsername(usernameLower).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                LOGGER.log(Level.WARNING, "Username conflict: {0} was used by {1}, now claimed by {2}. Clearing old profile's username.", new Object[]{usernameLower, other.getUuid(), ownerId});
                other.setUsername(null);
                save(other);
            }
        });
    }

    private void claimName(UUID ownerId, String name) {
        if (name == null || name.isEmpty()) return;
        repository.findByName(name).ifPresent(other -> {
            if (!ownerId.equals(other.getUuid())) {
                LOGGER.log(Level.WARNING, "Display name conflict: {0} was used by {1}, now claimed by {2}. Clearing old profile's name.", new Object[]{name, other.getUuid(), ownerId});
                other.setName(null);
                save(other);
            }
        });
    }

    private void update(UUID uuid, Consumer<Profile> changer, String actionContext) {
        repository.findByUuid(uuid).ifPresent(p -> {
            changer.accept(p);
            save(p);
            LOGGER.log(Level.FINE, "Profile updated for {0} via {1}", new Object[]{uuid, actionContext});
        });
    }

    private void publish(String action, Profile profile) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());
            node.put("id", profile.getId());
            node.put("name", profile.getName());
            node.put("username", profile.getUsername());
            node.put("cash", profile.getCash());
            node.put("premium", profile.isPremiumAccount());

            String json = node.toString();
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, json);
            LOGGER.log(Level.FINER, "Published profile sync message: {0}", json);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to publish profile sync message for " + profile.getUuid(), e);
        }
    }

    public void invalidateProfileCache(UUID uuid) {
        LOGGER.finest("Profile cache invalidation requested for " + uuid + " (no local cache in ProfileService)");
    }
}