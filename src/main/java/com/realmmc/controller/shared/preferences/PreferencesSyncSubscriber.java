package com.realmmc.controller.shared.preferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreferencesSyncSubscriber implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(PreferencesSyncSubscriber.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private RedisSubscriber subscriber;
    private Optional<PreferencesService> preferencesServiceOpt;

    public PreferencesSyncSubscriber(RedisSubscriber subscriber) {
        this.subscriber = subscriber;
        this.preferencesServiceOpt = ServiceRegistry.getInstance().getService(PreferencesService.class);
    }

    public void startListening() {
        if (subscriber != null && preferencesServiceOpt.isPresent()) {
            subscriber.registerListener(RedisChannel.PREFERENCES_SYNC, this);
            LOGGER.info("PreferencesSyncSubscriber registered on Redis channel PREFERENCES_SYNC.");
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PREFERENCES_SYNC.getName().equals(channel) || preferencesServiceOpt.isEmpty()) {
            return;
        }

        PreferencesService preferencesService = preferencesServiceOpt.get();

        try {
            JsonNode node = mapper.readTree(message);
            String uuidStr = node.path("uuid").asText(null);

            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                String langStr = node.path("language").asText(null);

                Language language = (langStr != null) ? Language.valueOf(langStr) : Language.getDefault();

                boolean staffChat = node.path("staffChatEnabled").asBoolean(true);
                boolean autoLogin = node.path("autoLogin").asBoolean(true);
                boolean session = node.path("sessionActive").asBoolean(true);

                preferencesService.updateCachedPreferences(uuid, language, staffChat, autoLogin, session);

                LOGGER.log(Level.FINE, "Received PREFERENCES_SYNC for {0}", uuid);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing PREFERENCES_SYNC message", e);
        }
    }

    public void stopListening() {
        LOGGER.info("PreferencesSyncSubscriber stopped.");
    }
}