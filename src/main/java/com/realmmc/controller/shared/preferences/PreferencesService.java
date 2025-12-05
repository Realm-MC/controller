package com.realmmc.controller.shared.preferences;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
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
    private final Map<UUID, Boolean> autoLoginCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sessionCache = new ConcurrentHashMap<>();

    public Optional<Preferences> getPreferences(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public Optional<Preferences> getOrLoadPreferences(UUID uuid) {
        Optional<Preferences> cached = getPreferences(uuid);
        if (cached.isEmpty()) {
            loadAndCachePreferences(uuid);
            return getPreferences(uuid);
        }
        return cached;
    }

    public Preferences ensurePreferences(Profile profile, Language initialLanguage) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile cannot be null for ensurePreferences");
        }
        return repository.findById(profile.getId()).orElseGet(() -> {
            LOGGER.info("Creating default preferences for profile ID: " + profile.getId());
            Language langToSet = (initialLanguage != null) ? initialLanguage : Language.getDefault();

            Preferences newPrefs = Preferences.builder()
                    .id(profile.getId())
                    .uuid(profile.getUuid())
                    .name(profile.getName())
                    .serverLanguage(langToSet)
                    .staffChatEnabled(true)
                    .autoLogin(true)
                    .sessionActive(true)
                    .build();

            repository.upsert(newPrefs);
            updateCachedPreferences(profile.getUuid(), langToSet, true, true, true);
            return newPrefs;
        });
    }

    public Preferences ensurePreferences(Profile profile) {
        return ensurePreferences(profile, null);
    }

    public Language toggleLanguage(UUID uuid) {
        Optional<Preferences> prefsOpt = getOrLoadPreferences(uuid);
        if (prefsOpt.isEmpty()) throw new IllegalStateException("Preferences not found for " + uuid);
        Preferences prefs = prefsOpt.get();
        Language next = (prefs.getServerLanguage() == Language.PORTUGUESE) ? Language.ENGLISH : Language.PORTUGUESE;
        prefs.setServerLanguage(next);
        save(prefs);
        return next;
    }

    public boolean toggleStaffChat(UUID uuid) {
        Optional<Preferences> prefsOpt = getOrLoadPreferences(uuid);
        if (prefsOpt.isEmpty()) throw new IllegalStateException("Preferences not found for " + uuid);
        Preferences prefs = prefsOpt.get();
        boolean newState = !prefs.isStaffChatEnabled();
        prefs.setStaffChatEnabled(newState);
        save(prefs);
        return newState;
    }

    public boolean toggleAutoLogin(UUID uuid) {
        Optional<Preferences> prefsOpt = getOrLoadPreferences(uuid);
        if (prefsOpt.isEmpty()) throw new IllegalStateException("Preferences not found for " + uuid);
        Preferences prefs = prefsOpt.get();
        boolean newState = !prefs.isAutoLogin();
        prefs.setAutoLogin(newState);
        save(prefs);
        return newState;
    }

    public boolean toggleSession(UUID uuid) {
        Optional<Preferences> prefsOpt = getOrLoadPreferences(uuid);
        if (prefsOpt.isEmpty()) throw new IllegalStateException("Preferences not found for " + uuid);
        Preferences prefs = prefsOpt.get();
        boolean newState = !prefs.isSessionActive();
        prefs.setSessionActive(newState);
        save(prefs);
        return newState;
    }

    public void updateIdentification(Profile profile) {
        if (profile == null) return;
        Preferences prefs = ensurePreferences(profile);
        if (profile.getName() != null && !profile.getName().equals(prefs.getName())) {
            prefs.setName(profile.getName());
            save(prefs);
        }
    }

    public void save(Preferences preferences) {
        repository.upsert(preferences);
        updateCachedPreferences(
                preferences.getUuid(),
                preferences.getServerLanguage(),
                preferences.isStaffChatEnabled(),
                preferences.isAutoLogin(),
                preferences.isSessionActive()
        );
        publishUpdate(preferences);
    }

    private void publishUpdate(Preferences preferences) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("uuid", preferences.getUuid().toString());
            node.put("language", preferences.getServerLanguage().name());
            node.put("staffChatEnabled", preferences.isStaffChatEnabled());
            node.put("autoLogin", preferences.isAutoLogin());
            node.put("sessionActive", preferences.isSessionActive());

            RedisPublisher.publish(RedisChannel.PREFERENCES_SYNC, node.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to publish preferences sync", e);
        }
    }

    public Optional<Language> getCachedLanguage(UUID uuid) {
        return Optional.ofNullable(languageCache.get(uuid));
    }
    public Optional<Boolean> getCachedStaffChatEnabled(UUID uuid) {
        return Optional.ofNullable(staffChatCache.get(uuid));
    }
    public Optional<Boolean> getCachedAutoLogin(UUID uuid) {
        return Optional.ofNullable(autoLoginCache.get(uuid));
    }
    public Optional<Boolean> getCachedSessionActive(UUID uuid) {
        return Optional.ofNullable(sessionCache.get(uuid));
    }

    public void updateCachedPreferences(UUID uuid, Language language, boolean staffChat, boolean autoLogin, boolean session) {
        if (uuid == null) return;
        if (language != null) languageCache.put(uuid, language);
        staffChatCache.put(uuid, staffChat);
        autoLoginCache.put(uuid, autoLogin);
        sessionCache.put(uuid, session);
    }

    public void removeCachedPreferences(UUID uuid) {
        languageCache.remove(uuid);
        staffChatCache.remove(uuid);
        autoLoginCache.remove(uuid);
        sessionCache.remove(uuid);
    }

    public void loadAndCachePreferences(UUID uuid) {
        Optional<Preferences> prefsOpt = getPreferences(uuid);

        Language lang = prefsOpt.map(Preferences::getServerLanguage).orElse(Language.getDefault());
        boolean staffChat = prefsOpt.map(Preferences::isStaffChatEnabled).orElse(true);
        boolean autoLogin = prefsOpt.map(Preferences::isAutoLogin).orElse(true);
        boolean session = prefsOpt.map(Preferences::isSessionActive).orElse(true);

        updateCachedPreferences(uuid, lang, staffChat, autoLogin, session);
    }

    public void checkAndSendStaffChatWarning(Object playerObject, UUID uuid) {
        try {
            if (getCachedStaffChatEnabled(uuid).orElse(true)) return;

            RoleService roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            roleService.getSessionDataFromCache(uuid).ifPresent(sessionData -> {
                if (sessionData.getPrimaryRole().getType() == RoleType.STAFF) {
                    Messages.send(playerObject, MessageKey.STAFFCHAT_WARN_DISABLED);
                    ServiceRegistry.getInstance().getService(SoundPlayer.class)
                            .ifPresent(sp -> sp.playSound(playerObject, SoundKeys.NOTIFICATION));
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao verificar aviso de StaffChat", e);
        }
    }
}