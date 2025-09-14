package com.realmmc.controller.shared.storage.redis;

public enum RedisChannel {
    CONTROLLER_BROADCAST("controller:broadcast"),
    PROFILES_SYNC("controller:profiles:sync");

    private final String name;

    RedisChannel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
