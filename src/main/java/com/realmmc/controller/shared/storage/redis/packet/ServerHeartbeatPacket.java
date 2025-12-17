package com.realmmc.controller.shared.storage.redis.packet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.realmmc.controller.modules.server.data.GameState;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerHeartbeatPacket implements RedisPacket {

    @JsonProperty("server")
    private String serverName;

    private ServerStatus status;
    private GameState gameState;
    private String mapName;
    private boolean canShutdown;

    @JsonProperty("players")
    private int playerCount;

    private int maxPlayers;

    @Override
    public RedisChannel getChannel() {
        return RedisChannel.SERVER_STATUS_UPDATE;
    }
}