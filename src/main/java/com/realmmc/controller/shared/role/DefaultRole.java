package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;

public enum DefaultRole {
    MASTER("master", "§6Master", "§6[Master] ", "", "§6", RoleType.STAFF, 1000,
            List.of("proxy.master", "controller.master", "rankup.master", "lobby.master", "login.master", "build.master", "core.master")),

    MANAGER("manager", "§4Gerente", "§4[Gerente] ", "", "§4", RoleType.STAFF, 900,
            List.of("proxy.manager", "controller.manager", "rankup.manager", "lobby.manager", "login.manager", "build.manager", "core.manager")),

    ADMINISTRATOR("administrator", "§cAdmin", "§c[Admin] ", "", "§c", RoleType.STAFF, 800,
            List.of("proxy.administrator", "controller.administrator", "rankup.administrator", "lobby.administrator", "login.administrator", "build.administrator", "core.administrator")),

    MODERATOR("moderator", "§2Moderador", "§2[Moderador] ", "", "§2", RoleType.STAFF, 700,
            List.of("proxy.moderator", "controller.moderator", "rankup.moderator", "lobby.moderator", "login.moderator", "build.moderator", "core.moderator")),

    HELPER("helper", "§eAjudante", "§e[Ajudante] ", "", "§e", RoleType.STAFF, 600,
            List.of("proxy.helper", "controller.helper", "rankup.helper", "lobby.helper", "login.helper", "build.helper", "core.helper")),

    PARTNER("partner", "§cParceiro", "§c[Parceiro] ", "", "§c", RoleType.STAFF, 550,
            List.of("proxy.partner", "controller.partner", "rankup.partner", "lobby.partner", "login.partner", "build.partner", "core.partner")),

    BUILDER("builder", "§3Construtor", "§3[Construtor] ", "", "§3", RoleType.STAFF, 500,
            List.of("proxy.builder", "controller.builder", "rankup.builder", "lobby.builder", "login.builder", "build.builder", "core.builder")),

    STAFF("staff", "§3Equipe", "§3[Equipe] ", "", "§3", RoleType.STAFF, 400,
            List.of("proxy.staff", "controller.staff", "rankup.staff", "lobby.staff", "login.staff", "build.staff", "core.staff")),

    SUPREME("supreme", "§4Supremo", "§4[Supremo] ", "", "§4", RoleType.VIP, 300,
            List.of("proxy.supreme", "controller.supreme", "rankup.supreme", "lobby.supreme", "login.supreme", "build.supreme", "core.supreme")),

    LEGENDARY("legendary", "§2Lendário", "§2[Lendário] ", "", "§2", RoleType.VIP, 250,
            List.of("proxy.legendary", "controller.legendary", "rankup.legendary", "lobby.legendary", "login.legendary", "build.legendary", "core.legendary")),

    HERO("hero", "§5Herói", "§5[Herói] ", "", "§5", RoleType.VIP, 200,
            List.of("proxy.hero", "controller.hero", "rankup.hero", "lobby.hero", "login.hero", "build.hero", "core.hero")),

    CHAMPION("champion", "§3Campeão", "§3[Campeão] ", "", "§3", RoleType.VIP, 150,
            List.of("proxy.champion", "controller.champion", "rankup.champion", "lobby.champion", "login.champion", "build.champion", "core.champion")),

    DEFAULT("default", "§7Membro", "§7", "", "§7", RoleType.DEFAULT, 0,
            List.of("proxy.default", "controller.default", "rankup.default", "lobby.default", "login.default", "build.default", "core.default"));

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