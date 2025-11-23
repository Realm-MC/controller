package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.ServerRegistryService;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(ServerStatusListener.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final ServerRegistryService registryService;
    private final ProxyServer proxyServer;

    public ServerStatusListener() {
        this.registryService = ServiceRegistry.getInstance().requireService(ServerRegistryService.class);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
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

            if (serverName != null && "ONLINE".equalsIgnoreCase(statusStr)) {
                LOGGER.info("[ServerStatus] Recebido sinal READY de: " + serverName);

                registryService.handleServerReadySignal(serverName);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar SERVER_STATUS_UPDATE", e);
        }
    }
}