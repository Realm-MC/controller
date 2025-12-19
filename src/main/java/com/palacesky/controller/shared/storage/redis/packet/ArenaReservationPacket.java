package com.palacesky.controller.shared.storage.redis.packet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArenaReservationPacket implements RedisPacket {

    private UUID playerUuid;
    private String arenaId;
    private String targetNode;
    private long timestamp;

    @Override
    public RedisChannel getChannel() {
        return RedisChannel.ARENA_RESERVATION;
    }
}