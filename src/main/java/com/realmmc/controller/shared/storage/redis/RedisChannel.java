package com.realmmc.controller.shared.storage.redis;

public enum RedisChannel {
    CONTROLLER_BROADCAST("controller:broadcast"),
    PROFILES_SYNC("controller:profiles:sync"),
    PREFERENCES_SYNC("controller:preferences:sync"),
    ROLE_SYNC("controller:roles:sync"),
    ROLE_BROADCAST("controller:roles:broadcast");

    private final String name;

    RedisChannel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}