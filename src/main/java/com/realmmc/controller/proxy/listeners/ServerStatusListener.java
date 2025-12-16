package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.ServerRegistryService;
import com.realmmc.controller.modules.server.data.GameState;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;

import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class ServerStatusListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(ServerStatusListener.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final ServerRegistryService registryService;

    public ServerStatusListener() {
        this.registryService = ServiceRegistry.getInstance().requireService(ServerRegistryService.class);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.SERVER_STATUS_UPDATE.getName().equals(channel)) {
            return;
        }

        try {
            JsonNode node = mapper.readTree(message);
            String serverName = node.path("server").asText(null);
            String statusStr = node.path("status").asText(null);

            if (serverName != null && statusStr != null) {
                String gameStateStr = node.path("gameState").asText("UNKNOWN");
                String mapName = node.path("mapName").asText(null);
                boolean canShutdown = node.path("canShutdown").asBoolean(true);
                int players = node.path("players").asInt(-1);

                GameState gameState;
                try {
                    gameState = GameState.valueOf(gameStateStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    gameState = GameState.UNKNOWN;
                }

                ServerStatus status;
                try {
                    status = ServerStatus.valueOf(statusStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    status = ServerStatus.OFFLINE;
                }

                registryService.updateServerHeartbeat(serverName, status, gameState, mapName, canShutdown, players);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar SERVER_STATUS_UPDATE: " + message, e);
        }
    }
}