package com.realmmc.controller.shared.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.shared.profile.Profile;
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
                    .build();
            repository.upsert(newPrefs);
            updateCachedLanguage(profile.getUuid(), langToSet);
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
        updateCachedLanguage(preferences.getUuid(), preferences.getServerLanguage());
        publishUpdate(preferences);
    }

    private void publishUpdate(Preferences preferences) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", preferences.getUuid().toString());
            node.put("language", preferences.getServerLanguage().name());

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

    public void updateCachedLanguage(UUID uuid, Language language) {
        if (language != null) {
            languageCache.put(uuid, language);
            LOGGER.log(Level.FINEST, "Updated language cache for {0} to {1}", new Object[]{uuid, language});
        } else {
            languageCache.remove(uuid);
            LOGGER.log(Level.FINEST, "Removed language cache for {0} due to null language", uuid);
        }
    }

    public void removeCachedLanguage(UUID uuid) {
        languageCache.remove(uuid);
        LOGGER.log(Level.FINEST, "Removed language cache for {0}", uuid);
    }

    public Language loadAndCacheLanguage(UUID uuid) {
        Language lang = getPreferences(uuid)
                .map(Preferences::getServerLanguage)
                .orElse(Language.getDefault());
        updateCachedLanguage(uuid, lang);
        return lang;
    }

}