package com.realmmc.controller.shared.messaging;

public enum MessageKey {
    ONLY_PLAYERS("only_players"),
    COMMAND_ERROR("command_error"),
    COMMON_PLAYER_NOT_ONLINE("common.player.not_online"),
    COMMON_PLAYER_NEVER_JOINED("common.player.never_joined"),
    COMMON_USAGE("common.usage"),
    COMMON_NO_PERMISSION_GROUP("common.no_permission.group"),
    COMMON_NO_PERMISSION_RANK("common.no_permission.rank"),
    COMMON_NO_PERMISSION_GENERIC("common.no_permission.generic"),

    COMMON_HELP_HEADER("common.help.header"),
    COMMON_HELP_LINE("common.help.line"),
    COMMON_HELP_FOOTER_FULL("common.help.footer.full"),
    COMMON_HELP_FOOTER_REQUIRED("common.help.footer.required"),

    COMMON_INFO_HEADER("common.info.header"),
    COMMON_INFO_LINE("common.info.line"),
    COMMON_INFO_BOOLEAN_TRUE("common.info.boolean.true"),
    COMMON_INFO_BOOLEAN_FALSE("common.info.boolean.false"),
    COMMON_INFO_LIST_HEADER("common.info.list.header"),
    COMMON_INFO_LIST_ITEM("common.info.list.item"),
    COMMON_INFO_LIST_EMPTY("common.info.list.empty"),

    PROFILE_ROLE_EXPIRING_SOON("profile.role.expiring_soon"),
    PROFILE_ROLE_JUST_EXPIRED("profile.role.just_expired"),

    DISPLAY_SPAWNED("display.spawned"),
    DISPLAY_CLONED("display.cloned"),
    DISPLAY_REMOVED("display.removed"),
    DISPLAY_LIST_EMPTY("display.list.empty"),
    DISPLAY_LIST_HEADER("display.list.header"),
    DISPLAY_RELOADED("display.reloaded"),
    DISPLAY_TELEPORTED("display.teleported"),
    DISPLAY_ITEM_SET("display.item_set"),
    DISPLAY_SCALE_SET("display.scale_set"),
    DISPLAY_BILLBOARD_SET("display.billboard_set"),
    DISPLAY_GLOW_TOGGLED("display.glow_toggled"),
    DISPLAY_LINES_TOGGLED("display.lines_toggled"),
    DISPLAY_LINE_ADDED("display.line_added"),
    DISPLAY_LINE_SET("display.line_set"),
    DISPLAY_LINE_REMOVED("display.line_removed"),
    DISPLAY_ACTION_ADDED("display.action_added"),
    DISPLAY_ACTION_REMOVED("display.action_removed"),
    DISPLAY_INVALID_ID("display.invalid_id"),
    DISPLAY_NOT_FOUND("display.not_found"),
    DISPLAY_INVALID_MATERIAL("display.invalid_material"),
    DISPLAY_INVALID_SCALE("display.invalid_scale"),
    DISPLAY_INVALID_BILLBOARD("display.invalid_billboard"),
    DISPLAY_INVALID_LINE("display.invalid_line"),
    DISPLAY_INVALID_ACTION_LINE("display.invalid_action_line"),

    NPC_CREATED("npc.created"),
    NPC_CLONED("npc.cloned"),
    NPC_REMOVED("npc.removed"),
    NPC_RENAMED("npc.renamed"),
    NPC_LIST_EMPTY("npc.list.empty"),
    NPC_LIST_HEADER("npc.list.header"),
    NPC_NOT_FOUND("npc.not_found"),
    NPC_INVALID_ID("npc.invalid_id"),
    NPC_TELEPORTED("npc.teleported"),
    NPC_SKIN_SET("npc.skin_set"),
    NPC_NAME_TOGGLED("npc.name_toggled"),
    NPC_LOOK_TOGGLED("npc.look_toggled"),
    NPC_LINE_ADDED("npc.line_added"),
    NPC_LINE_SET("npc.line_set"),
    NPC_LINE_REMOVED("npc.line_removed"),
    NPC_ACTION_ADDED("npc.action_added"),
    NPC_ACTION_REMOVED("npc.action_removed"),

    PARTICLE_CREATED("particle.created"),
    PARTICLE_CLONED("particle.cloned"),
    PARTICLE_REMOVED("particle.removed"),
    PARTICLE_NOT_FOUND("particle.not_found"),
    PARTICLE_INVALID_ID("particle.invalid_id"),
    PARTICLE_INVALID_TYPE("particle.invalid_type"),
    PARTICLE_INVALID_PROPERTY("particle.invalid_property"),
    PARTICLE_INVALID_VALUE("particle.invalid_value"),
    PARTICLE_LIST_EMPTY("particle.list.empty"),
    PARTICLE_LIST_HEADER("particle.list.header"),
    PARTICLE_TELEPORTED("particle.teleported"),
    PARTICLE_PROPERTY_SET("particle.property_set"),
    PARTICLE_ANIMATION_SET("particle.animation_set"),
    PARTICLE_ANIMATION_STOPPED("particle.animation_stopped"),
    PARTICLE_TESTED("particle.tested"),
    PARTICLE_RELOADED("particle.reloaded"),

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