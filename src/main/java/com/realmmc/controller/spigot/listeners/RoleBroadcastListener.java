package com.realmmc.controller.spigot.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RoleBroadcastListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RoleBroadcastListener.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoleBroadcastListener() {
        LOGGER.info("RoleBroadcastListener (Spigot) inicializado.");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_BROADCAST.getName().equals(channel)) return;

        try {
            JsonNode node = objectMapper.readTree(message);
            String playerName = node.path("playerName").asText("Alguém");
            String playerColor = node.path("playerColor").asText("");
            String groupDisplay = node.path("groupDisplay").asText("um grupo");

            String playerColoredName = (playerColor.isEmpty() ? "<white>" : playerColor) + playerName;

            Message titleMsg = Message.of(MessageKey.ROLE_BROADCAST_TITLE);
            Message subtitleMsg = Message.of(MessageKey.ROLE_BROADCAST_SUBTITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName)
                    .with("group_display", groupDisplay);

            for (Player p : Bukkit.getOnlinePlayers()) {
                Messages.sendTitle(p, titleMsg, subtitleMsg);
            }

            LOGGER.info("[RoleBroadcast] Título enviado para " + Bukkit.getOnlinePlayers().size() + " jogadores locais.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar Broadcast de Role: " + message, e);
        }
    }
}