package com.palacesky.controller.spigot.entities.npcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MineskinResolver {
    private static final Logger LOGGER = Logger.getLogger(MineskinResolver.class.getName());
    private static final String MINESKIN_API_URL = "https://api.mineskin.org/generate/url";
    private final ObjectMapper mapper = new ObjectMapper();

    public TextureProperty resolveFromUrl(String imageUrl) {
        try {
            URL url = new URL(MINESKIN_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonInputString = "{\"url\": \"" + imageUrl + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.warning("Mineskin API respondeu com erro " + responseCode + " para a URL: " + imageUrl);
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                JsonNode root = mapper.readTree(response.toString());
                JsonNode textureNode = root.path("data").path("texture");
                String value = textureNode.path("value").asText(null);
                String signature = textureNode.path("signature").asText(null);

                if (value != null && signature != null) {
                    return new TextureProperty("textures", value, signature);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao resolver skin da URL: " + imageUrl, e);
        }
        return null;
    }
}