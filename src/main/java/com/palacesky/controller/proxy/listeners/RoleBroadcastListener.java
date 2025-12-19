package com.palacesky.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class RoleBroadcastListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RoleBroadcastListener.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProxyServer proxyServer;

    public RoleBroadcastListener() {
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        LOGGER.info("RoleBroadcastListener (Proxy) inicializado.");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_BROADCAST.getName().equals(channel)) return;

        try {
            JsonNode node = objectMapper.readTree(message);
            String playerName = node.path("playerName").asText("Alguém");
            String playerColor = node.path("playerColor").asText("<white>");
            String groupDisplay = node.path("groupDisplay").asText("um grupo");
            String playerColoredName = playerColor + playerName;

            Message titleMsg = Message.of(MessageKey.ROLE_BROADCAST_TITLE);

            Message subtitleMsg = Message.of(MessageKey.ROLE_BROADCAST_SUBTITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName)
                    .with("player_color", playerColor)
                    .with("group_display", groupDisplay);

            for (Player p : proxyServer.getAllPlayers()) {
                Messages.sendTitle(p, titleMsg, subtitleMsg);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro no RoleBroadcastListener (Proxy): " + message, e);
        }
    }
}