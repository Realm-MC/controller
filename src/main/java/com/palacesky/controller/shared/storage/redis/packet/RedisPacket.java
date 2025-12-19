package com.palacesky.controller.shared.storage.redis.packet;

import com.palacesky.controller.shared.storage.redis.RedisChannel;

public interface RedisPacket {
    RedisChannel getChannel();
}