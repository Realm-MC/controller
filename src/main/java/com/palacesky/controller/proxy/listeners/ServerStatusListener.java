package com.palacesky.controller.proxy.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.server.ServerRegistryService;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.packet.ServerHeartbeatPacket;

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
            ServerHeartbeatPacket packet = mapper.readValue(message, ServerHeartbeatPacket.class);

            if (packet.getServerName() != null && packet.getStatus() != null) {

                registryService.updateServerHeartbeat(
                        packet.getServerName(),
                        packet.getStatus(),
                        packet.getGameState(),
                        packet.getMapName(),
                        packet.isCanShutdown(),
                        packet.getPlayerCount()
                );
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar ServerHeartbeatPacket: " + message, e);
        }
    }
}