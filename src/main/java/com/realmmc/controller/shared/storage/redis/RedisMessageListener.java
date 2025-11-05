package com.realmmc.controller.shared.storage.redis;

public interface RedisMessageListener {
    void onMessage(String channel, String message);
}
