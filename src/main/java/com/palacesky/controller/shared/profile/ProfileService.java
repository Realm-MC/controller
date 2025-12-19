package com.palacesky.controller.shared.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.logs.CashLog;
import com.palacesky.controller.shared.logs.LogRepository;
import com.palacesky.controller.shared.logs.LogType;
import com.palacesky.controller.shared.preferences.Language;
import com.palacesky.controller.shared.preferences.PreferencesService;
import com.palacesky.controller.shared.role.PlayerRole;
import com.palacesky.controller.shared.session.SessionTrackerService;
import com.palacesky.controller.shared.stats.StatisticsService;
import com.palacesky.controller.shared.storage.mongodb.MongoSequences;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisPublisher;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.utils.TaskScheduler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileService {

    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());
    private final ProfileRepository repository = new ProfileRepository();
    private final LogRepository.Cash cashLogRepository = new LogRepository.Cash();
    private final ObjectMapper mapper = new ObjectMapper();

    public LogRepository.Cash getCashLogRepository() {
        return cashLogRepository;
    }

    private void logCash(UUID uuid, String name, String source, LogType action, int amount, int oldBal, int newBal) {
        int logId = MongoSequences.getNext("logsCash");
        String idStr = String.valueOf(logId);

        CashLog log = CashLog.builder()
                .id(idStr)
                .targetUuid(uuid)
                .targetName(name)
                .source(source != null ? source : "Console")
                .action(action)
                .amount(amount)
                .oldBalance(oldBal)
                .newBalance(newBal)
                .timestamp(System.currentTimeMillis())
                .build();
        TaskScheduler.runAsync(() -> cashLogRepository.insert(log));
    }

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
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                profileOpt = repository.findByUuid(uuid);
            }
            return profileOpt;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Error fetching profile by UUID: " + uuid, e);
            return Optional.empty();
        }
    }

    public Optional<Profile> getById(int id) {
        try { return repository.findById(id); } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<Profile> getByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try { return repository.findByName(name); } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<Profile> getByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try { return repository.findByUsername(username.toLowerCase()); } catch (Exception e) { return Optional.empty(); }
    }

    public List<Profile> findByActiveRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) return Collections.emptyList();
        try { return repository.findByActiveRoleName(roleName); } catch (Exception e) { return Collections.emptyList(); }
    }

    public int calculateOfflineEarnings(UUID uuid, long lastLogout) { return 0; }

    public long countAccountsByIp(String ip) {
        if (ip == null) return 0;
        try { return repository.collection().countDocuments(Filters.eq("firstIp", ip)); } catch (Exception e) { return 0; }
    }

    public void updateLastLogout(UUID uuid) {
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        try {
            repository.collection().updateOne(Filters.eq("uuid", uuid),
                    Updates.combine(Updates.set("lastLogout", now), Updates.set("updatedAt", now)));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update lastLogout for " + uuid, e);
        }
    }

    public void save(Profile profile) {
        Objects.requireNonNull(profile, "Profile cannot be null for saving.");
        long now = System.currentTimeMillis();
        if (profile.getCreatedAt() == 0L) profile.setCreatedAt(now);
        profile.setUpdatedAt(now);

        try {
            if (profile.getId() == null) {
                profile.setId(MongoSequences.getNext("profiles"));
            }
            repository.upsert(profile);
            publish("upsert", profile);
            updateSessionData(profile.getUuid(), profile.getCash(), profile.getPrimaryRoleName(), profile.getEquippedMedal());
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
            Profile dummy = new Profile(); dummy.setUuid(uuid);
            profileOpt.ifPresent(p -> dummy.setId(p.getId()));
            publish("delete", dummy);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ProfileService] Unexpected error deleting profile for UUID: " + uuid, e);
        }
    }

    public boolean exists(UUID uuid) {
        if (uuid == null) return false;
        try { return repository.collection().countDocuments(Filters.eq("uuid", uuid)) > 0; }
        catch (Exception e) { return false; }
    }

    public Profile ensureProfile(UUID loginUuid, String displayName, String username, String currentIp,
                                 String clientVersion, String clientType, boolean isPremium, Object playerObject) {
        Optional<Profile> profileOptByUuid = getByUuid(loginUuid);
        final AtomicBoolean needsSave = new AtomicBoolean(false);
        Profile profileToReturn;

        if (profileOptByUuid.isPresent()) {
            profileToReturn = profileOptByUuid.get();
            profileToReturn.setLastLogin(System.currentTimeMillis());
            needsSave.set(true);

            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(finalProfileForServices, null));

        } else {
            long now = System.currentTimeMillis();
            int profileId = MongoSequences.getNext("profiles");

            Language initialLangTemp = Language.getDefault();
            try { initialLangTemp = Messages.determineInitialLanguage(playerObject); } catch (Exception e) {}
            final Language initialLang = initialLangTemp;

            profileToReturn = Profile.builder()
                    .id(profileId)
                    .uuid(loginUuid)
                    .name(displayName)
                    .username(username.toLowerCase())
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
            final Profile finalProfileForServices = profileToReturn;
            getStatsService().ifPresent(stats -> stats.ensureStatistics(finalProfileForServices));
            getPreferencesService().ifPresent(prefs -> prefs.ensurePreferences(finalProfileForServices, initialLang));
        }

        if (needsSave.get()) {
            try { save(profileToReturn); }
            catch (MongoException e) {
                if (e.getCode() == 11000) profileToReturn = getByUuid(loginUuid).orElseThrow();
                else throw e;
            }
        }

        final Profile finalProfileToReturn = profileToReturn;
        getPreferencesService().ifPresent(prefs -> prefs.loadAndCachePreferences(finalProfileToReturn.getUuid()));

        return finalProfileToReturn;
    }

    public void updateName(UUID uuid, String newName) {
        if (uuid == null || newName == null) return;
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

    public void addCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount <= 0) return;

        org.bson.conversions.Bson filter = Filters.eq("uuid", targetUuid);
        org.bson.conversions.Bson update = Updates.combine(
                Updates.inc("cash", amount),
                Updates.inc("pendingCash", amount),
                Updates.set("updatedAt", System.currentTimeMillis())
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER)
                .upsert(false);

        Profile updatedProfile = repository.collection().findOneAndUpdate(filter, update, options);

        if (updatedProfile != null) {
            try {
                ObjectNode node = mapper.createObjectNode();
                node.put("uuid", targetUuid.toString());
                node.put("amount", amount);
                RedisPublisher.publish(RedisChannel.CASH_NOTIFICATION, node.toString());
            } catch (Exception e) {
                LOGGER.warning("Failed to publish cash notification: " + e.getMessage());
            }

            updateLocalAndPublish(targetUuid, p -> {
                int oldBalance = p.getCash();

                p.setCash(updatedProfile.getCash());
                p.setPendingCash(updatedProfile.getPendingCash());
                p.setUpdatedAt(updatedProfile.getUpdatedAt());

                logCash(targetUuid, p.getName(), sourceName, LogType.ADD, amount, oldBalance, p.getCash());
            });
        }
    }

    public void resetPendingCash(UUID targetUuid) {
        if (targetUuid == null) return;

        org.bson.conversions.Bson filter = Filters.eq("uuid", targetUuid);
        org.bson.conversions.Bson update = Updates.combine(
                Updates.set("pendingCash", 0),
                Updates.set("updatedAt", System.currentTimeMillis())
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER);

        Profile updatedProfile = repository.collection().findOneAndUpdate(filter, update, options);

        if (updatedProfile != null) {
            updateLocalAndPublish(targetUuid, p -> {
                p.setPendingCash(0);
                p.setUpdatedAt(updatedProfile.getUpdatedAt());
            });
        }
    }

    public boolean removeCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount <= 0) return false;

        org.bson.conversions.Bson filter = Filters.and(Filters.eq("uuid", targetUuid), Filters.gte("cash", amount));
        org.bson.conversions.Bson update = Updates.combine(
                Updates.inc("cash", -amount),
                Updates.set("updatedAt", System.currentTimeMillis())
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER);

        Profile updatedProfile = repository.collection().findOneAndUpdate(filter, update, options);

        if (updatedProfile != null) {
            updateLocalAndPublish(targetUuid, p -> {
                int oldBalance = p.getCash();
                p.setCash(updatedProfile.getCash());
                p.setUpdatedAt(updatedProfile.getUpdatedAt());

                logCash(targetUuid, p.getName(), sourceName, LogType.REMOVE, amount, oldBalance, p.getCash());
            });
            return true;
        }
        return false;
    }

    public void setCash(UUID targetUuid, int amount, UUID sourceUuid, String sourceName) {
        if (targetUuid == null || amount < 0) return;

        org.bson.conversions.Bson filter = Filters.eq("uuid", targetUuid);
        org.bson.conversions.Bson update = Updates.combine(
                Updates.set("cash", amount),
                Updates.set("updatedAt", System.currentTimeMillis())
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .returnDocument(ReturnDocument.AFTER);

        Profile updatedProfile = repository.collection().findOneAndUpdate(filter, update, options);

        if (updatedProfile != null) {
            updateLocalAndPublish(targetUuid, p -> {
                int oldBalance = p.getCash();
                if (oldBalance != amount) {
                    p.setCash(updatedProfile.getCash());
                    p.setUpdatedAt(updatedProfile.getUpdatedAt());
                    logCash(targetUuid, p.getName(), sourceName, LogType.SET, amount, oldBalance, p.getCash());
                }
            });
        }
    }

    public void clearCash(UUID targetUuid, UUID sourceUuid, String sourceName) {
        setCash(targetUuid, 0, sourceUuid, sourceName);
    }

    private void updateLocalAndPublish(UUID uuid, Consumer<Profile> action) {
        Optional<Profile> localOpt = getByUuid(uuid);
        if (localOpt.isPresent()) {
            Profile p = localOpt.get();
            action.accept(p);
            publish("upsert", p);
            updateSessionData(uuid, p.getCash(), p.getPrimaryRoleName(), p.getEquippedMedal());
        }
    }

    private void update(UUID uuid, ProfileModifier modifier, String actionContext) {
        Optional<Profile> profileOpt = getByUuid(uuid);
        if (profileOpt.isPresent()) {
            Profile profile = profileOpt.get();
            boolean changed = false;
            try { changed = modifier.modify(profile); } catch (Exception e) { return; }
            if (changed) save(profile);
        }
    }

    @FunctionalInterface private interface ProfileModifier { boolean modify(Profile profile); }

    private void publish(String action, Profile profile) {
        if (profile == null || profile.getUuid() == null) return;
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("action", action);
            node.put("uuid", profile.getUuid().toString());
            if ("upsert".equals(action)) {
                if (profile.getId() != null) node.put("id", profile.getId());
                node.put("name", profile.getName());
                node.put("username", profile.getUsername());
                node.put("cash", profile.getCash());
                node.put("pendingCash", profile.getPendingCash());
                node.put("premium", profile.isPremiumAccount());
                node.put("equippedMedal", profile.getEquippedMedal());
                node.put("primaryRoleName", profile.getPrimaryRoleName());

                if (profile.getRoles() != null) {
                    ArrayNode rolesNode = node.putArray("roles");
                    for (PlayerRole pr : profile.getRoles()) {
                        ObjectNode roleInfo = rolesNode.addObject();
                        roleInfo.put("roleName", pr.getRoleName());
                        roleInfo.put("status", pr.getStatus().name());
                        if (pr.getExpiresAt() != null) roleInfo.put("expiresAt", pr.getExpiresAt());
                        roleInfo.put("paused", pr.isPaused());
                        if (pr.getPausedTimeRemaining() != null) roleInfo.put("pausedTimeRemaining", pr.getPausedTimeRemaining());
                    }
                }
                node.put("updatedAt", profile.getUpdatedAt());
            }
            RedisPublisher.publish(RedisChannel.PROFILES_SYNC, node.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to publish profile sync", e);
        }
    }

    public List<Profile> getTopCash(int limit) {
        try { return repository.findTopByCash(limit); } catch (Exception e) { return Collections.emptyList(); }
    }
}