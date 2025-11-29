package com.realmmc.controller.shared.storage.redis;

public enum RedisChannel {
    CONTROLLER_BROADCAST("controller:broadcast"),
    PROFILES_SYNC("controller:profiles:sync"),
    COSMETICS_SYNC("controller:cosmetics:sync"),
    PREFERENCES_SYNC("controller:preferences:sync"),
    ROLE_SYNC("controller:roles:sync"),
    ROLE_BROADCAST("controller:roles:broadcast"),
    ROLES_UPDATE("controller:roles:update"),
    ROLE_NOTIFICATION("controller:roles:notification"),
    STAFF_CHAT("controller:staffchat"),
    SERVER_STATUS_UPDATE("controller:server:status"),
    GLOBAL_PLAYER_COUNT("controller:global:playercount"),
    GLOBAL_NETWORK_MAX_PLAYERS("controller:global:networkmaxplayers"),
    CASH_NOTIFICATION("controller:cash:notification");

    private final String name;

    RedisChannel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}