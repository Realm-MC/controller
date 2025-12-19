package com.palacesky.controller.spigot.entities.npcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lightweight resolver for Mojang skins (value/signature) from player name or UUID.
 */
public class MojangSkinResolver {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Resolve skin textures. Accepts either a player name or a UUID (with or without hyphens).
     * Returns null if not found or on error.
     */
    public TextureProperty resolveByNameOrUuid(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            String uuidNoHyphen;
            if (looksLikeUuid(input)) {
                uuidNoHyphen = stripHyphens(input);
            } else {
                String name = input;
                URL uuidUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                HttpURLConnection uuidConn = (HttpURLConnection) uuidUrl.openConnection();
                uuidConn.setConnectTimeout(4000);
                uuidConn.setReadTimeout(6000);
                uuidConn.setRequestMethod("GET");
                if (uuidConn.getResponseCode() != 200) return null;
                JsonNode uuidNode = mapper.readTree(uuidConn.getInputStream());
                uuidNoHyphen = uuidNode.path("id").asText(null);
                if (uuidNoHyphen == null || uuidNoHyphen.isEmpty()) return null;
            }

            URL sessionUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoHyphen + "?unsigned=false");
            HttpURLConnection sessionConn = (HttpURLConnection) sessionUrl.openConnection();
            sessionConn.setConnectTimeout(4000);
            sessionConn.setReadTimeout(6000);
            sessionConn.setRequestMethod("GET");
            if (sessionConn.getResponseCode() != 200) return null;
            JsonNode prof = mapper.readTree(sessionConn.getInputStream());
            for (JsonNode prop : prof.path("properties")) {
                if ("textures".equals(prop.path("name").asText())) {
                    String value = prop.path("value").asText(null);
                    String sig = prop.path("signature").asText(null);
                    if (value != null && sig != null) {
                        TextureProperty tp = new TextureProperty("textures", value, sig);
                        return tp;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean looksLikeUuid(String s) {
        String hex = stripHyphens(s);
        if (hex.length() != 32) return false;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    private static String stripHyphens(String s) {
        return s.replace("-", "");
    }
}
