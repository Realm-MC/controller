package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum DefaultRole {
    OWNER(1, "owner", "Owner", "&4&lOWNER &4", List.of("*"), 1000),
    ADMIN(2, "admin", "Admin", "&c&lADMIN &c", List.of("controller.*", "minecraft.command.*"), 900),
    MOD(3, "mod", "Moderador", "&5&lMOD &5", List.of("controller.mod.*", "minecraft.command.kick", "minecraft.command.ban"), 800),
    HELPER(4, "helper", "Ajudante", "&9&lHELPER &9", List.of("controller.helper.*", "minecraft.command.kick"), 700),
    VIP_PLUS(5, "vip_plus", "VIP+", "&6&lVIP+ &6", List.of("controller.vip.plus"), 600),
    VIP(6, "vip", "VIP", "&a&lVIP &a", List.of("controller.vip"), 500),
    MEMBER(7, "member", "Membro", "&7", List.of("controller.member.*"), 0);

    private final int id;
    private final String name;
    private final String displayName;
    private final String prefix;
    private final List<String> permissions;
    private final int weight;

    DefaultRole(int id, String name, String displayName, String prefix, List<String> permissions, int weight) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.prefix = prefix;
        this.permissions = permissions;
        this.weight = weight;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public List<String> getPermissions() { return permissions; }
    public int getWeight() { return weight; }

    public Role toRole() {
        return Role.builder()
                .id(id)
                .name(name)
                .displayName(displayName)
                .prefix(prefix)
                .permissions(new ArrayList<>(permissions))
                .weight(weight)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
    }
}