package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

public enum DefaultRole {

    // Formato: (name, displayName, prefix, suffix, color, type, weight, permissions, inheritance)
    MASTER("master", "<gold>Master", "<gold>[Master] ", "", "<gold>", RoleType.STAFF, 1000,
            Arrays.asList("*"),
            Arrays.asList("manager")),

    MANAGER("manager", "<dark_red>Gerente", "<dark_red>[Gerente] ", "", "<dark_red>", RoleType.STAFF, 900,
            Arrays.asList("controller.manager"),
            Arrays.asList("administrator")),

    ADMINISTRATOR("administrator", "<red>Administrador", "<red>[Admin] ", "", "<red>", RoleType.STAFF, 800,
            Arrays.asList("controller.administrator"),
            Arrays.asList("moderator")),

    MODERATOR("moderator", "<dark_green>Moderador", "<dark_green>[Moderador] ", "", "<dark_green>", RoleType.STAFF, 700,
            Arrays.asList("controller.moderator"),
            Arrays.asList("helper")),

    HELPER("helper", "<yellow>Ajudante", "<yellow>[Ajudante] ", "", "<yellow>", RoleType.STAFF, 600,
            Arrays.asList("controller.helper"),
            Arrays.asList("partner")),

    PARTNER("partner", "<red>Parceiro", "<red>[Parceiro] ", "", "<red>", RoleType.VIP, 500,
            Arrays.asList("controller.partner"),
            Arrays.asList("supreme")),

    SUPREME("supreme", "<dark_red>Supremo", "<dark_red>[Supremo] ", "", "<dark_red>", RoleType.VIP, 400,
            Arrays.asList("controller.supreme"),
            Arrays.asList("legendary")),

    LEGENDARY("legendary", "<dark_green>Lendário", "<dark_green>[Lendário] ", "", "<dark_green>", RoleType.VIP, 300,
            Arrays.asList("controller.legendary"),
            Arrays.asList("hero")),

    HERO("hero", "<dark_purple>Herói", "<dark_purple>[Herói] ", "", "<dark_purple>", RoleType.VIP, 200,
            Arrays.asList("controller.hero"),
            Arrays.asList("champion")),

    CHAMPION("champion", "<blue>Campeão", "<dark_blue>[Campeão] ", "", "<dark_blue>", RoleType.VIP, 100,
            Arrays.asList("controller.champion"),
            Arrays.asList("default")),

    DEFAULT("default", "<gray>Membro", "<gray>", "", "<gray>", RoleType.DEFAULT, 0,
            Arrays.asList("controller.default"),
            Collections.emptyList());

    private final String name;
    private final String displayName;
    private final String prefix;
    private final String suffix;
    private final String color;
    private final RoleType type;
    private final int weight;
    private final List<String> permissions;
    private final List<String> inheritance;

    DefaultRole(String name, String displayName, String prefix, String suffix, String color, RoleType type, int weight, List<String> permissions, List<String> inheritance) {
        this.name = name;
        this.displayName = displayName;
        this.prefix = prefix;
        this.suffix = suffix;
        this.color = color;
        this.type = type;
        this.weight = weight;
        this.permissions = permissions;
        this.inheritance = inheritance;
    }

    // Getters
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public String getSuffix() { return suffix; }
    public String getColor() { return color; }
    public RoleType getType() { return type; }
    public int getWeight() { return weight; }
    public List<String> getPermissions() { return new ArrayList<>(permissions); }
    public List<String> getInheritance() { return new ArrayList<>(inheritance); }

    public Role toRole() {
        long now = System.currentTimeMillis();
        return Role.builder()
                .name(name.toLowerCase())
                .displayName(displayName)
                .prefix(prefix)
                .suffix(suffix)
                .color(color)
                .type(type)
                .weight(weight)
                .permissions(new ArrayList<>(permissions))
                .inheritance(new ArrayList<>(inheritance))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}