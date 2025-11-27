package com.realmmc.controller.shared.cosmetics.medals;

import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.sounds.SoundKeys;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public enum Medal {

    PVP_KING("pvp_king", "<yellow>⚔ ", "", "<yellow>Rei do PvP",
            MedalRarity.LENDARIA, MedalCategory.PVP,
            "DIAMOND_SWORD",
            Arrays.asList("<gray>Concedida ao jogador que", "<gray>dominou a arena de batalha."),
            -1,
            Arrays.asList(ServerType.LOBBY, ServerType.PUNISHED),
            SoundKeys.SUCCESS),

    BETA_TESTER("beta", "<aqua>[BETA] ", "", "<aqua>Beta Tester",
            MedalRarity.EPICA, MedalCategory.ESPECIAL,
            "BEACON",
            Arrays.asList("<gray>Participou da fase de testes", "<gray>beta do servidor."),
            -1,
            Collections.emptyList(),
            SoundKeys.NOTIFICATION),

    SUPPORTER("supporter", "<light_purple>❤ ", "", "<light_purple>Apoiador",
            MedalRarity.RARA, MedalCategory.VIP,
            "HEART_OF_THE_SEA",
            Arrays.asList("<gray>Um apoiador fiel", "<gray>da nossa comunidade."),
            5000,
            Collections.emptyList(),
            SoundKeys.CLICK),

    RICH("rich", "<green>$$$ ", "", "<green>Magnata",
            MedalRarity.UNICA, MedalCategory.GERAL,
            "GOLD_INGOT",
            Arrays.asList("<gray>O jogador mais rico", "<gray>de toda a rede (Top 1)."),
            -1,
            Collections.emptyList(),
            SoundKeys.SUCCESS),

    EVENT_WINNER("event", "<gold>🏆 ", " <gold><bold>VENCEDOR", "<gold>Vencedor",
            MedalRarity.EVENTO, MedalCategory.EVENTOS,
            "NETHER_STAR",
            Arrays.asList("<gray>Venceu um evento", "<gray>oficial da Staff."),
            -1,
            Collections.emptyList(),
            SoundKeys.SUCCESS);

    private final String id;
    private final String prefix;
    private final String suffix;
    private final String displayName;
    private final MedalRarity rarity;
    private final MedalCategory category;
    private final String iconMaterial;
    private final List<String> lore;
    private final int price;
    private final List<ServerType> allowedServers;
    private final String equipSound;

    Medal(String id, String prefix, String suffix, String displayName,
          MedalRarity rarity, MedalCategory category, String iconMaterial,
          List<String> lore, int price, List<ServerType> allowedServers, String equipSound) {
        this.id = id;
        this.prefix = prefix;
        this.suffix = suffix;
        this.displayName = displayName;
        this.rarity = rarity;
        this.category = category;
        this.iconMaterial = iconMaterial;
        this.lore = lore;
        this.price = price;
        this.allowedServers = allowedServers != null ? allowedServers : Collections.emptyList();
        this.equipSound = equipSound;
    }

    public static Optional<Medal> fromId(String id) {
        if (id == null || id.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
                .filter(m -> m.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public boolean isAllowedOn(ServerType type) {
        if (allowedServers.isEmpty()) return true;
        return allowedServers.contains(type);
    }
}