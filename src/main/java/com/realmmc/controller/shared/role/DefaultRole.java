package com.realmmc.controller.shared.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public enum DefaultRole {

    // --- STAFF (Alto Escalão) ---
    MASTER("master", "<gold>Master", "<gold>[Master] ", "", "<gold>", RoleType.STAFF, 1000,
            Arrays.asList("*"),
            Arrays.asList("manager")),

    MANAGER("manager", "<dark_red>Gerente", "<dark_red>[Gerente] ", "", "<dark_red>", RoleType.STAFF, 900,
            Arrays.asList("controller.manager"),
            Arrays.asList("coordinator")),

    COORDINATOR("coordinator", "<red>Coordenador", "<red>[Coord] ", "", "<red>", RoleType.STAFF, 800,
            Arrays.asList("controller.coordinator"),
            Arrays.asList("moderator")),

    MODERATOR("moderator", "<dark_green>Moderador", "<dark_green>[Mod] ", "", "<dark_green>", RoleType.STAFF, 700,
            Arrays.asList("controller.moderator"),
            Arrays.asList("helper")),

    HELPER("helper", "<yellow>Ajudante", "<yellow>[Ajudante] ", "", "<yellow>", RoleType.STAFF, 600,
            Arrays.asList("controller.helper"),
            Arrays.asList("builder")),

    // --- CONSTRUÇÃO ---
    // Herda de YouTuber para ter acesso a cosméticos/perks enquanto constrói, se desejar.
    BUILDER("builder", "<green>Construtor", "<green>[Builder] ", "", "<green>", RoleType.STAFF, 550,
            Arrays.asList("controller.builder"),
            Arrays.asList("youtuber")),

    // --- MEDIA / PARCEIROS ---
    YOUTUBER("youtuber", "<red>YouTuber", "<red>[YT] ", "", "<red>", RoleType.VIP, 500,
            Arrays.asList("controller.youtuber"),
            Arrays.asList("streamer")),

    STREAMER("streamer", "<blue>Streamer", "<blue>[Stream] ", "", "<blue>", RoleType.VIP, 450,
            Arrays.asList("controller.streamer"),
            Arrays.asList("tiktoker")),

    TIKTOKER("tiktoker", "<gray>TikToker", "<gray>[TikTok] ", "", "<gray>", RoleType.VIP, 400,
            Arrays.asList("controller.tiktoker"),
            Arrays.asList("beta")),

    // --- BETA / TESTADORES ---
    BETA("beta", "<dark_aqua>Beta", "<dark_aqua>[Beta] ", "", "<dark_aqua>", RoleType.VIP, 350,
            Arrays.asList("controller.beta"),
            Arrays.asList("majestade")),

    // --- VIPS (Do maior para o menor) ---
    MAJESTADE("majestade", "<gold>Majestade", "<gold>[Majestade] ", "", "<gold>", RoleType.VIP, 300,
            Arrays.asList("controller.majestade"),
            Arrays.asList("legendary")),

    LEGENDARY("legendary", "<dark_red>Lendário", "<dark_red>[Lendário] ", "", "<dark_red>", RoleType.VIP, 250,
            Arrays.asList("controller.legendary"),
            Arrays.asList("imperial")),

    IMPERIAL("imperial", "<aqua>Imperial", "<aqua>[Imperial] ", "", "<aqua>", RoleType.VIP, 200,
            Arrays.asList("controller.imperial"),
            Arrays.asList("real")),

    REAL("real", "<green>Real", "<green>[Real] ", "", "<green>", RoleType.VIP, 150,
            Arrays.asList("controller.real"),
            Arrays.asList("nobre")),

    NOBRE("nobre", "<dark_green>Nobre", "<dark_green>[Nobre] ", "", "<dark_green>", RoleType.VIP, 100,
            Arrays.asList("controller.nobre"),
            Arrays.asList("default")),

    // --- DEFAULT ---
    DEFAULT("default", "<gray>Membro", "<gray>", "", "<gray>", RoleType.DEFAULT, 0,
            Arrays.asList("controller.default"), // Permissões básicas
            Arrays.asList()); // Base da pirâmide

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