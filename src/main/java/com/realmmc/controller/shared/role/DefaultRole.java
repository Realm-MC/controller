package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;

public enum DefaultRole {
    MASTER("master", "&6Master", "&6[Master] ", "", "&6", RoleType.STAFF, 1000, List.of("*")),
    MANAGER("manager", "&4Gerente", "&4[Gerente] ", "", "&4", RoleType.STAFF, 900, List.of("controller.admin.*")),
    ADMINISTRATOR("administrator", "&cAdmin", "&c[Admin] ", "", "&c", RoleType.STAFF, 800, List.of("controller.admin.*")),
    MODERATOR("moderator", "&2Moderador", "&2[Moderador] ", "", "&2", RoleType.STAFF, 700, List.of("controller.mod.*")),
    HELPER("helper", "&eAjudante", "&e[Ajudante] ", "", "&e", RoleType.STAFF, 600, List.of("controller.helper.*")),
    PARTNER("partner", "&cParceiro", "&c[Parceiro] ", "", "&c", RoleType.STAFF, 550, List.of("controller.vip")),
    BUILDER("builder", "&3Construtor", "&3[Construtor] ", "", "&3", RoleType.STAFF, 500, List.of("controller.builder.*")),
    STAFF("staff", "&3Equipe", "&3[Equipe] ", "", "&3", RoleType.STAFF, 400, List.of()),

    SUPREME("supreme", "&4Supremo", "&4[Supremo] ", "", "&4", RoleType.VIP, 300, List.of("controller.vip.supreme")),
    LEGENDARY("legendary", "&2Lendário", "&2[Lendário] ", "", "&2", RoleType.VIP, 250, List.of("controller.vip.legendary")),
    HERO("hero", "&5Herói", "&5[Herói] ", "", "&5", RoleType.VIP, 200, List.of("controller.vip.hero")),
    CHAMPION("champion", "&3Campeão", "&3[Campeão] ", "", "&3", RoleType.VIP, 150, List.of("controller.vip.champion")),

    DEFAULT("default", "&7Membro", "&7", "", "&7", RoleType.DEFAULT, 0, List.of("controller.member.*"));

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