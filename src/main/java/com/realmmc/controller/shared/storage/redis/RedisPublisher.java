package com.realmmc.controller.shared.storage.redis;

import redis.clients.jedis.Jedis;

public final class RedisPublisher {
    private RedisPublisher() {}

    public static void publish(RedisChannel channel, String message) {
        publish(channel.getName(), message);
    }

    public static void publish(String channel, String message) {
        try (Jedis jedis = RedisManager.getResource()) {
            jedis.publish(channel, message);
        }
    }
}
