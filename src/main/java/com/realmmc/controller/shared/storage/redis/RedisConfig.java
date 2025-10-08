package com.realmmc.controller.shared.storage.redis;

public record RedisConfig(String host, int port, String password, int database, boolean ssl) {
}
