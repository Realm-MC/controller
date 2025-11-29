package com.realmmc.controller.shared.cosmetics.medals;

import lombok.Getter;

@Getter
public enum MedalRarity {
    COMUM("<gray>Comum"),
    INCOMUM("<green>Incomum"),
    RARA("<blue>Rara"),
    EPICA("<light_purple>Épica"),
    LENDARIA("<gold>Lendária"),
    EVENTO("<aqua>Evento"),
    SAZONAL("<red>Sazonal"),
    UNICA("<dark_red><b>ÚNICA</b>");

    private final String displayName;

    MedalRarity(String displayName) {
        this.displayName = displayName;
    }
}