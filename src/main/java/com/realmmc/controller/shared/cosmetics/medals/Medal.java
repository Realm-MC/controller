package com.realmmc.controller.shared.cosmetics.medals;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum Medal {

    NONE("none", "", "", "<grey>Nenhuma"),

    BETA("beta", "<aqua>[BETA] ", "", "<aqua>Beta"),

    ALPHA("alpha", "<aqua>[ALPHA] ", "", "");

    private final String id;
    private final String prefix;
    private final String suffix;
    private final String displayName;

    Medal(String id, String prefix, String suffix, String displayName) {
        this.id = id;
        this.prefix = prefix;
        this.suffix = suffix;
        this.displayName = displayName;
    }

    public static Optional<Medal> fromId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(m -> m.id.equalsIgnoreCase(id))
                .findFirst();
    }
}