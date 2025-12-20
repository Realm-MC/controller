package com.realmmc.controller.modules.server.data;

public enum ServerType {
    LOGIN,
    LOBBY,
    PUNISHED,
    PERSISTENT,
    // Novos tipos de minigames
    HIDEANDSEEK,
    BEDWARS,
    SKYWARS;

    // Helper para saber se é um minigame (usa Arcade)
    public boolean isArcade() {
        return this == HIDEANDSEEK || this == BEDWARS || this == SKYWARS;
    }
}