package com.realmmc.controller.shared.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.UUID;

public class ProfileSyncSubscriber {
    private final RedisSubscriber subscriber = new RedisSubscriber();
    private final ProfileService profiles = new ProfileService();
    private final ObjectMapper mapper = new ObjectMapper();

    public void start() {
        subscriber.registerListener(RedisChannel.PROFILES_SYNC, new RedisMessageListener() {
            @Override
            public void onMessage(String channel, String message) {
                handle(message);
            }
        });
        subscriber.start();
    }

    public void stop() {
        subscriber.stop();
    }

    private void handle(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String action = node.path("action").asText("");
            String uuidStr = node.path("uuid").asText(null);
            if (uuidStr == null) {
                String legacy = node.path("id").asText(null);
                if (legacy != null) uuidStr = legacy;
            }
            if (uuidStr == null) return;
            UUID id = UUID.fromString(uuidStr);

            profiles.getByUuid(id).ifPresent(p -> {
                boolean changed = false;
                if (node.hasNonNull("name")) { p.setName(node.get("name").asText()); changed = true; }
                if (node.hasNonNull("username")) { p.setUsername(node.get("username").asText()); changed = true; }
                if (node.hasNonNull("lastIp")) { p.setLastIp(node.get("lastIp").asText()); changed = true; }
                if (node.hasNonNull("lastClientVersion")) { p.setLastClientVersion(node.get("lastClientVersion").asText()); changed = true; }
                if (node.hasNonNull("lastClientType")) { p.setLastClientType(node.get("lastClientType").asText()); changed = true; }
                if (node.has("cash")) { p.setCash(node.get("cash").asInt()); changed = true; }
                if (node.has("cashTopPosition")) { p.setCashTopPosition(node.get("cashTopPosition").isNull() ? null : node.get("cashTopPosition").asInt()); changed = true; }
                if (node.has("cashTopPositionEnteredAt")) { p.setCashTopPositionEnteredAt(node.get("cashTopPositionEnteredAt").isNull() ? null : node.get("cashTopPositionEnteredAt").asLong()); changed = true; }
                if (changed) {
                    profiles.save(p);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
