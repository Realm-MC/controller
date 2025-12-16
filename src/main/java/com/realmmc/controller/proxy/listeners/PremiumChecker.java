package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.annotations.Listeners;
import com.velocitypowered.api.util.GameProfile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Listeners
public class PremiumChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 300;
    private static final String BASE_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String BASE_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";

    public record PremiumCheckResult(boolean premium, GameProfile profile, String reason) {}

    private static String requestJsonString(String url) {
        for (int i = 1; i <= MAX_RETRIES; i++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        return response.toString();
                    }
                } else if (responseCode == 204 || responseCode == 404) {
                    return "{}";
                }

            } catch (Exception e) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (i < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    public static PremiumCheckResult checkPremium(String username) {
        String uuidJsonString = requestJsonString(BASE_UUID + username);
        if (uuidJsonString == null) {
            return new PremiumCheckResult(false, null, "uuid_request_failed");
        }

        try {
            JsonNode uuidJson = MAPPER.readTree(uuidJsonString);
            if (uuidJson == null || !uuidJson.has("id")) {
                return new PremiumCheckResult(false, null, null);
            }

            String idNoDash = uuidJson.get("id").asText();
            String profileJsonString = requestJsonString(BASE_PROFILE + idNoDash + "?unsigned=false");

            if (profileJsonString != null) {
                return buildResultFromJson(username, uuidJson, MAPPER.readTree(profileJsonString));
            } else {
                return new PremiumCheckResult(false, null, "profile_request_failed");
            }
        } catch (Exception e) {
            return new PremiumCheckResult(false, null, "processing_exception");
        }
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