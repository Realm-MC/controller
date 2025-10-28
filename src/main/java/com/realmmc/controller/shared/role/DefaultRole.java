package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays; // Import Arrays

public enum DefaultRole {

    // Formato: (name, displayName, prefix, suffix, color, type, weight, permissions, inheritance)
    MASTER("master", "<gold>Master", "<gold>[Master] ", "", "<gold>", RoleType.STAFF, 1000,
            Arrays.asList("*"), // Usar Arrays.asList ou List.of() se Java 9+
            Arrays.asList("manager")),

    MANAGER("manager", "<dark_red>Gerente", "<dark_red>[Gerente] ", "", "<dark_red>", RoleType.STAFF, 900,
            Arrays.asList("controller.manager"),
            Arrays.asList("administrator")),

    ADMINISTRATOR("administrator", "<red>Admin", "<red>[Admin] ", "", "<red>", RoleType.STAFF, 800,
            Arrays.asList("controller.administrator"),
            Arrays.asList("moderator")),

    MODERATOR("moderator", "<green>Moderador", "<green>[Moderador] ", "", "<green>", RoleType.STAFF, 700,
            Arrays.asList("controller.moderator"),
            Arrays.asList("helper")),

    HELPER("helper", "<yellow>Ajudante", "<yellow>[Ajudante] ", "", "<yellow>", RoleType.STAFF, 600,
            Arrays.asList("controller.helper"),
            Arrays.asList("staff")), // Helper herda de Staff (base)

    PARTNER("partner", "<dark_aqua>Parceiro", "<dark_aqua>[Parceiro] ", "", "<dark_aqua>", RoleType.STAFF, 550, // Cor e Peso ajustados
            Arrays.asList("controller.partner"),
            Arrays.asList("supreme")), // Partner herda de Supreme

    BUILDER("builder", "<blue>Construtor", "<blue>[Construtor] ", "", "<blue>", RoleType.STAFF, 500,
            Arrays.asList("controller.builder"),
            Arrays.asList("staff")), // Builder herda de Staff (base)

    STAFF("staff", "<blue>Equipe", "<blue>[Equipe] ", "", "<blue>", RoleType.STAFF, 400,
            Arrays.asList("controller.staff"), // Grupo base da staff
            Arrays.asList("default")), // Staff herda de Default (para perms básicas)

    SUPREME("supreme", "<dark_red>Supremo", "<dark_red>[Supremo] ", "", "<dark_red>", RoleType.VIP, 300,
            Arrays.asList("controller.supreme"), // Permissões VIP
            Arrays.asList("legendary")),

    LEGENDARY("legendary", "<green>Lendário", "<green>[Lendário] ", "", "<green>", RoleType.VIP, 250,
            Arrays.asList("controller.legendary"),
            Arrays.asList("hero")),

    HERO("hero", "<dark_purple>Herói", "<dark_purple>[Herói] ", "", "<dark_purple>", RoleType.VIP, 200,
            Arrays.asList("controller.hero"),
            Arrays.asList("champion")),

    CHAMPION("champion", "<blue>Campeão", "<blue>[Campeão] ", "", "<blue>", RoleType.VIP, 150,
            Arrays.asList("controller.champion"),
            Arrays.asList("default")), // VIPs herdam de Default

    DEFAULT("default", "<gray>Membro", "<gray>", "", "<gray>", RoleType.DEFAULT, 0,
            Arrays.asList("controller.default"), // Permissões básicas para todos
            Arrays.asList()); // Grupo base, não herda de ninguém

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
    public List<String> getPermissions() { return new ArrayList<>(permissions); } // Retorna cópia
    public List<String> getInheritance() { return new ArrayList<>(inheritance); } // Retorna cópia

    /**
     * Converte este enum num objeto Role (POJO) para o MongoDB.
     */
    public Role toRole() {
        long now = System.currentTimeMillis();
        return Role.builder()
                .name(name.toLowerCase()) // Garante ID minúsculo
                .displayName(displayName)
                .prefix(prefix)
                .suffix(suffix)
                .color(color)
                .type(type)
                .weight(weight)
                .permissions(new ArrayList<>(permissions)) // Cria nova lista
                .inheritance(new ArrayList<>(inheritance)) // Cria nova lista
                .createdAt(now) // Define createdAt
                .updatedAt(now) // Define updatedAt
                .build();
    }
}