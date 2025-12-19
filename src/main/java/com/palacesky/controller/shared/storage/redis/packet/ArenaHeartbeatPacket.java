package com.palacesky.controller.shared.storage.redis.packet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.palacesky.controller.modules.server.data.GameState;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArenaHeartbeatPacket implements RedisPacket {

    private String arenaId;
    private String gameType;
    private String nodeName;
    private GameState state;
    private int currentPlayers;
    private int maxPlayers;
    private String mapName;

    @Override
    public RedisChannel getChannel() {
        return RedisChannel.ARENA_HEARTBEAT;
    }
}