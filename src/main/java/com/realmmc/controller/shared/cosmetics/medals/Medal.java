package com.realmmc.controller.shared.cosmetics.medals;

import com.realmmc.controller.modules.server.data.ServerType;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public enum Medal {

    NONE("none", "", "", Collections.emptyList()),

    PVP_KING("pvp_king", "<yellow>⚔ ", "<yellow><b>Rei do PvP</b>",
            Arrays.asList(ServerType.LOBBY, ServerType.PUNISHED)),

    BETA_TESTER("beta", "<aqua><b>[BETA]</b> ", "<aqua>Beta Tester",
            Collections.singletonList(ServerType.LOBBY)),

    SUPPORTER("supporter", "<light_purple>❤ ", "<light_purple>Apoiador",
            Collections.emptyList()),

    RICH("rich", "<green>$$$ ", "<green>Magnata",
            Arrays.asList(ServerType.LOBBY, ServerType.PERSISTENT)),

    EVENT_WINNER("event", "<gold>🏆 ", "<gold>Vencedor de Evento",
            Collections.emptyList());

    private final String id;
    private final String prefix;
    private final String displayName;
    private final List<ServerType> allowedTypes;

    Medal(String id, String prefix, String displayName, List<ServerType> allowedTypes) {
        this.id = id;
        this.prefix = prefix;
        this.displayName = displayName;
        this.allowedTypes = allowedTypes;
    }

    public static Optional<Medal> fromId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(m -> m.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public boolean isVisibleOn(ServerType type) {
        if (this == NONE) return false;
        if (allowedTypes == null || allowedTypes.isEmpty()) return true;
        return allowedTypes.contains(type);
    }
}