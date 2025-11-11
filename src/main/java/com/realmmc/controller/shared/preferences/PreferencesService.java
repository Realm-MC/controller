package com.realmmc.controller.shared.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreferencesService {

    private final PreferencesRepository repository = new PreferencesRepository();
    private static final Logger LOGGER = Logger.getLogger(PreferencesService.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<UUID, Language> languageCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> staffChatCache = new ConcurrentHashMap<>();

    public Optional<Preferences> getPreferences(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public Preferences ensurePreferences(Profile profile, Language initialLanguage) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile cannot be null for ensurePreferences");
        }
        return repository.findById(profile.getId()).orElseGet(() -> {
            LOGGER.info("Creating default preferences for profile ID: " + profile.getId() + " (UUID: " + profile.getUuid() + ")");
            Language langToSet = (initialLanguage != null) ? initialLanguage : Language.getDefault();

            Preferences newPrefs = Preferences.builder()
                    .id(profile.getId())
                    .uuid(profile.getUuid())
                    .name(profile.getName())
                    .serverLanguage(langToSet)
                    .staffChatEnabled(true)
                    .build();

            repository.upsert(newPrefs);
            updateCachedPreferences(profile.getUuid(), langToSet, true);
            return newPrefs;
        });
    }

    public Preferences ensurePreferences(Profile profile) {
        return ensurePreferences(profile, null);
    }


    public void updateIdentification(Profile profile) {
        if (profile == null) return;
        Preferences prefs = ensurePreferences(profile);
        boolean changed = false;
        if (profile.getName() != null && !profile.getName().equals(prefs.getName())) {
            prefs.setName(profile.getName());
            changed = true;
        }
        if (changed) {
            save(prefs);
        }
    }

    public void setLanguage(UUID uuid, Language language) {
        repository.findByUuid(uuid).ifPresent(prefs -> {
            if (prefs.getServerLanguage() != language) {
                prefs.setServerLanguage(language);
                save(prefs);
                LOGGER.info("Set language to " + language + " for UUID: " + uuid);
            }
        });
    }

    public void save(Preferences preferences) {
        repository.upsert(preferences);
        updateCachedPreferences(preferences.getUuid(), preferences.getServerLanguage(), preferences.isStaffChatEnabled());
        publishUpdate(preferences);
    }

    private void publishUpdate(Preferences preferences) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", preferences.getUuid().toString());
            node.put("language", preferences.getServerLanguage().name());
            node.put("staffChatEnabled", preferences.isStaffChatEnabled());

            String json = node.toString();
            RedisPublisher.publish(RedisChannel.PREFERENCES_SYNC, json);
            LOGGER.log(Level.FINER, "Published preferences sync message: {0}", json);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to publish preferences sync message for " + preferences.getUuid(), e);
        }
    }

    public Optional<Language> getCachedLanguage(UUID uuid) {
        return Optional.ofNullable(languageCache.get(uuid));
    }

    public Optional<Boolean> getCachedStaffChatEnabled(UUID uuid) {
        return Optional.ofNullable(staffChatCache.get(uuid));
    }

    public void updateCachedPreferences(UUID uuid, Language language, boolean staffChatEnabled) {
        if (uuid == null) return;

        if (language != null) {
            languageCache.put(uuid, language);
        } else {
            languageCache.remove(uuid);
        }

        staffChatCache.put(uuid, staffChatEnabled);
        LOGGER.log(Level.FINEST, "Updated preferences cache for {0} (Lang: {1}, SC: {2})", new Object[]{uuid, language, staffChatEnabled});
    }

    public void removeCachedPreferences(UUID uuid) {
        languageCache.remove(uuid);
        staffChatCache.remove(uuid);
        LOGGER.log(Level.FINEST, "Removed preferences cache for {0}", uuid);
    }

    public void loadAndCachePreferences(UUID uuid) {
        Optional<Preferences> prefsOpt = getPreferences(uuid);

        Language lang = prefsOpt.map(Preferences::getServerLanguage).orElse(Language.getDefault());
        boolean staffChat = prefsOpt.map(Preferences::isStaffChatEnabled).orElse(true);

        updateCachedPreferences(uuid, lang, staffChat);
    }

    public void checkAndSendStaffChatWarning(Object playerObject, UUID uuid) {
        try {
            boolean isEnabled = getCachedStaffChatEnabled(uuid).orElse(true);

            if (isEnabled) {
                return;
            }

            RoleService roleService = ServiceRegistry.getInstance().requireService(RoleService.class);

            roleService.getSessionDataFromCache(uuid).ifPresent(sessionData -> {
                if (sessionData.getPrimaryRole().getType() == RoleType.STAFF) {

                    Messages.send(playerObject, MessageKey.STAFFCHAT_WARN_DISABLED);

                    ServiceRegistry.getInstance().getService(SoundPlayer.class)
                            .ifPresent(sp -> sp.playSound(playerObject, SoundKeys.NOTIFICATION));
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao verificar ou enviar aviso de StaffChat para " + uuid, e);
        }
    }
}