package com.realmmc.controller.modules.server.data;

import lombok.Getter;

@Getter
public enum DefaultServer {

    // IPs definidos como 127.0.0.1 para segurança. O ServerManager deve atualizar isso se dinâmico.
    LOGIN("login-1", "Login", "31.97.90.152", 25566, "d79f737e", 500, 500, "default", ServerType.LOGIN),
    LOBBY("lobby-1", "Lobby 1", "31.97.90.152", 25567, "727c7884", 100, 150, "default", ServerType.LOBBY),
    BUILD("build-1", "Construção", "31.97.90.152", 25569, "384ce2a7", 100, 100, "administrator", ServerType.PERSISTENT),
    RANKUP("rankup-1", "Rankup", "31.97.90.152", 25568, "856fbec5", 300, 500, "default", ServerType.PERSISTENT);

    private final String name;
    private final String displayName;
    private final String ip;
    private final int port;
    private final String pterodactylId;
    private final int maxPlayers;
    private final int maxPlayersVip;
    private final String minGroup;
    private final ServerType type;

    DefaultServer(String name, String displayName, String ip, int port, String pterodactylId, int maxPlayers, int maxPlayersVip, String minGroup, ServerType type) {
        this.name = name;
        this.displayName = displayName;
        this.ip = ip;
        this.port = port;
        this.pterodactylId = pterodactylId;
        this.maxPlayers = maxPlayers;
        this.maxPlayersVip = maxPlayersVip;
        this.minGroup = minGroup;
        this.type = type;
    }

    public ServerInfo toServerInfo() {
        return ServerInfo.builder()
                .name(name)
                .displayName(displayName)
                .ip(ip)
                .port(port)
                .pterodactylId(pterodactylId)
                .maxPlayers(maxPlayers)
                .maxPlayersVip(maxPlayersVip)
                .minGroup(minGroup)
                .type(type)
                .status(ServerStatus.OFFLINE)
                .gameState(GameState.UNKNOWN)
                .canShutdown(true)
                .playerCount(0)
                .build();
    }
}