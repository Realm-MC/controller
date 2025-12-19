package com.palacesky.controller.shared.storage.redis;

public interface RedisMessageListener {
    void onMessage(String channel, String message);
}
