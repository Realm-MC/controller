package com.realmmc.controller.shared.cosmetics.medals;

import lombok.Getter;

@Getter
public enum MedalCategory {
    GERAL("Geral"),
    PVP("PvP"),
    EVENTOS("Eventos"),
    VIP("VIP"),
    ESPECIAL("Especial");

    private final String displayName;

    MedalCategory(String displayName) {
        this.displayName = displayName;
    }
}