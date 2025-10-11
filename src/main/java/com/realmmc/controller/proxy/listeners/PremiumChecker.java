package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.velocitypowered.api.util.GameProfile;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PremiumChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_MS = 2000;
    private static final String BASE_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String BASE_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final String CACHE_PREFIX = "controller:premium_cache:";
    private static final int CACHE_EXPIRATION_SECONDS = 86400; // 24 horas
    private static final String NON_PREMIUM_MARKER = "{\"is_premium\":false}";

    public record PremiumCheckResult(boolean premium, GameProfile profile, String reason) {}

    private record CachedProfile(String uuidJson, String profileJson) {}

    private static String requestJsonString(String url) {
        try {
            var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (connection.getResponseCode() != 200) {
                return null;
            }

            try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            System.err.printf("[PremiumChecker] Falha ao requisitar %s: %s%n", url, e.getMessage());
            return null;
        }
    }

    public static PremiumCheckResult checkPremium(String username) {
        String cacheKey = CACHE_PREFIX + username.toLowerCase();

        try (Jedis jedis = RedisManager.getResource()) {
            String cachedData = jedis.get(cacheKey);
            if (cachedData != null) {
                if (cachedData.equals(NON_PREMIUM_MARKER)) {
                    return new PremiumCheckResult(false, null, "not_found_in_cache");
                }
                try {
                    // Cache hit: Sabemos que este jogador É premium, reconstrói o resultado
                    CachedProfile profile = MAPPER.readValue(cachedData, CachedProfile.class);
                    JsonNode uuidJson = MAPPER.readTree(profile.uuidJson());
                    JsonNode profileJson = MAPPER.readTree(profile.profileJson());
                    return buildResultFromJson(username, uuidJson, profileJson);
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            System.err.println("[PremiumChecker] Erro ao aceder ao Redis: " + e.getMessage());
        }

        String uuidJsonString = requestJsonString(BASE_UUID + username);
        if (uuidJsonString == null) {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.setex(cacheKey, CACHE_EXPIRATION_SECONDS, NON_PREMIUM_MARKER);
            } catch (Exception ignored) {}
            return new PremiumCheckResult(false, null, "uuid_not_found");
        }

        String profileJsonString = null;
        try {
            JsonNode uuidJson = MAPPER.readTree(uuidJsonString);
            String idNoDash = uuidJson.get("id").asText();
            profileJsonString = requestJsonString(BASE_PROFILE + idNoDash + "?unsigned=false");

            if (profileJsonString != null) {
                try (Jedis jedis = RedisManager.getResource()) {
                    CachedProfile toCache = new CachedProfile(uuidJsonString, profileJsonString);
                    jedis.setex(cacheKey, CACHE_EXPIRATION_SECONDS, MAPPER.writeValueAsString(toCache));
                } catch (Exception ignored) {}

                return buildResultFromJson(username, uuidJson, MAPPER.readTree(profileJsonString));
            }

        } catch (Exception e) {
        }

        return new PremiumCheckResult(false, null, "profile_fetch_failed");
    }

    private static PremiumCheckResult buildResultFromJson(String username, JsonNode uuidJson, JsonNode profileJson) {
        try {
            String idNoDash = uuidJson.get("id").asText();
            UUID uuid = UUID.fromString(idNoDash.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

            List<GameProfile.Property> properties = new ArrayList<>();
            if (profileJson.has("properties") && profileJson.get("properties").isArray()) {
                for (var prop : profileJson.get("properties")) {
                    properties.add(new GameProfile.Property(
                            prop.path("name").asText(""),
                            prop.path("value").asText(""),
                            prop.path("signature").asText(null)
                    ));
                }
            }

            String finalName = profileJson.has("name") ? profileJson.get("name").asText() : username;
            GameProfile velocityProfile = new GameProfile(uuid, finalName, properties);
            return new PremiumCheckResult(true, velocityProfile, null);
        } catch (Exception e) {
            return new PremiumCheckResult(false, null, "json_parse_error");
        }
    }
}