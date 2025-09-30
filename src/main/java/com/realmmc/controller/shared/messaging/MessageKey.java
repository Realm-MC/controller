package com.realmmc.controller.shared.messaging;

public enum MessageKey {
    ONLY_PLAYERS("only_players"),
    COMMAND_ERROR("command_error"),
    INVALID_USAGE("invalid_usage"),

    DISPLAY_SPAWNED("display.spawned"),
    DISPLAY_CLEARED("display.cleared"),
    DISPLAY_INVALID_ID("display.invalid_id"),
    DISPLAY_NOT_FOUND("display.not_found"),
    DISPLAY_ALL_SPAWNED("display.all_spawned"),
    DISPLAY_NO_ENTRIES("display.no_entries"),

    PROFILE_LOADED("profile.loaded"),
    PROFILE_SAVED("profile.saved"),
    PROFILE_ERROR("profile.error"),

    TEST_COMMAND("test.command"),
    TEST_WELCOME("test.welcome"),

    SYSTEM_STARTUP("system.startup"),
    SYSTEM_SHUTDOWN("system.shutdown"),
    MODULE_ENABLED("module.enabled"),
    MODULE_DISABLED("module.disabled");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}