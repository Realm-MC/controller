package com.realmmc.controller.shared.storage.redis.packet;

import com.realmmc.controller.shared.storage.redis.RedisChannel;

public interface RedisPacket {
    RedisChannel getChannel();
}