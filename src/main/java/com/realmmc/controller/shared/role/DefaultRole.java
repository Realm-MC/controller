package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;

public enum DefaultRole {
    MASTER("master", "<gold>Master", "<gold>[Master] ", "", "<gold>", RoleType.STAFF, 1000, List.of("*")),
    MANAGER("manager", "<dark_red>Gerente", "<dark_red>[Gerente] ", "", "<dark_red>", RoleType.STAFF, 900, List.of("controller.manager")),
    ADMINISTRATOR("administrator", "<red>Admin", "<red>[Admin] ", "", "<red>", RoleType.STAFF, 800, List.of("controller.administrator")),
    MODERATOR("moderator", "<dark_green>Moderador", "<dark_green>[Moderador] ", "", "<dark_green>", RoleType.STAFF, 700, List.of("controller.moderator")),
    HELPER("helper", "<yellow>Ajudante", "<yellow>[Ajudante] ", "", "<yellow>", RoleType.STAFF, 600, List.of("controller.helper")),
    PARTNER("partner", "<red>Parceiro", "<red>[Parceiro] ", "", "<red>", RoleType.STAFF, 550, List.of("controller.partner")),
    BUILDER("builder", "<dark_aqua>Construtor", "<dark_aqua>[Construtor] ", "", "<dark_aqua>", RoleType.STAFF, 500, List.of("controller.builder")),
    STAFF("staff", "<dark_aqua>Equipe", "<dark_aqua>[Equipe] ", "", "<dark_aqua>", RoleType.STAFF, 400, List.of()),

    SUPREME("supreme", "<dark_red>Supremo", "<dark_red>[Supremo] ", "", "<dark_red>", RoleType.VIP, 300, List.of("controller.supreme")),
    LEGENDARY("legendary", "<dark_green>Lendário", "<dark_green>[Lendário] ", "", "<dark_green>", RoleType.VIP, 250, List.of("controller.legendary")),
    HERO("hero", "<dark_purple>Herói", "<dark_purple>[Herói] ", "", "<dark_purple>", RoleType.VIP, 200, List.of("controller.hero")),
    CHAMPION("champion", "<dark_aqua>Campeão", "<dark_aqua>[Campeão] ", "", "<dark_aqua>", RoleType.VIP, 150, List.of("controller.champion")),

    DEFAULT("default", "<gray>Membro", "<gray>", "", "<gray>", RoleType.DEFAULT, 0, List.of("controller.default"));

    private final String name;
    private final String displayName;
    private final String prefix;
    private final String suffix;
    private final String color;
    private final RoleType type;
    private final int weight;
    private final List<String> permissions;

    DefaultRole(String name, String displayName, String prefix, String suffix, String color, RoleType type, int weight, List<String> permissions) {
        this.name = name;
        this.displayName = displayName;
        this.prefix = prefix;
        this.suffix = suffix;
        this.color = color;
        this.type = type;
        this.weight = weight;
        this.permissions = permissions;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getPrefix() { return prefix; }
    public String getSuffix() { return suffix; }
    public String getColor() { return color; }
    public RoleType getType() { return type; }
    public int getWeight() { return weight; }
    public List<String> getPermissions() { return permissions; }

    public int getId() {
        return this.ordinal() + 1;
    }

    public Role toRole() {
        return Role.builder()
                .id(getId())
                .name(name)
                .displayName(displayName)
                .prefix(prefix)
                .suffix(suffix)
                .color(color)
                .type(type)
                .weight(weight)
                .permissions(new ArrayList<>(permissions))
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
    }
}