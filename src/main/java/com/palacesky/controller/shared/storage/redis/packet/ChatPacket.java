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
public class ChatPacket implements RedisPacket {

    private String channelId;
    private String serverOrigin;
    private UUID senderUuid;
    private String senderName;
    private String senderDisplayName;
    private String message;
    private String permissionRequired;

    @Override
    public RedisChannel getChannel() {
        return RedisChannel.CHAT_CHANNEL;
    }
}