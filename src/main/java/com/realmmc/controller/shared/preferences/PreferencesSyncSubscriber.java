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
        if (preferencesServiceOpt.isEmpty()) {
            LOGGER.severe("PreferencesService not found! PreferencesSyncSubscriber cannot update cache.");
        }
    }

    public void startListening() {
        if (subscriber != null && preferencesServiceOpt.isPresent()) {
            subscriber.registerListener(RedisChannel.PREFERENCES_SYNC, this);
            LOGGER.info("PreferencesSyncSubscriber registered on Redis channel PREFERENCES_SYNC.");
        } else {
            LOGGER.severe("Cannot start PreferencesSyncSubscriber: Missing subscriber or PreferencesService.");
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
            String langStr = node.path("language").asText(null);
            JsonNode staffChatNode = node.path("staffChatEnabled");

            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                Language language = (langStr != null) ? Language.valueOf(langStr) : Language.getDefault();
                boolean staffChatEnabled = staffChatNode.isBoolean() ? staffChatNode.asBoolean() : true;

                preferencesService.updateCachedPreferences(uuid, language, staffChatEnabled);

                LOGGER.log(Level.FINE, "Received PREFERENCES_SYNC for {0}, updated local cache (Lang: {1}, SC: {2})", new Object[]{uuid, language, staffChatEnabled});

            } else {
                LOGGER.warning("Received invalid PREFERENCES_SYNC message: " + message);
            }

        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid UUID or Language Enum received on PREFERENCES_SYNC: " + message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing PREFERENCES_SYNC message", e);
        }
    }

    public void stopListening() {
        LOGGER.info("PreferencesSyncSubscriber stopped.");
    }
}