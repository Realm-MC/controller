package com.realmmc.controller.modules.server.data;

import lombok.Getter;

@Getter
public enum DefaultServer {

    // Formato: (name, displayName, ip, port, pterodactylId, maxPlayers, maxPlayersVip, minGroup, type)
    LOGIN("login-1", "Login", "31.97.90.152", 25566, "d79f737e-d704-4506-8e25-96f72dc4dec3", 500, 500, "default", ServerType.LOGIN),
    LOBBY("lobby-1", "Lobby 1", "31.97.90.152", 25567, "727c7884-b639-4058-89c3-dbb2ee553412", 100, 150, "default", ServerType.LOBBY),
    BUILD("build-1", "Construção", "31.97.90.152", 25569, "384ce2a7-bc79-4f0c-a0e7-d8b317e5fcf3", 100, 100, "administrator", ServerType.PERSISTENT),
    RANKUP("rankup-1", "Rankup", "31.97.90.152", 25568, "856fbec5-471c-4d55-8b8b-b205936d143c", 300, 500, "default", ServerType.PERSISTENT);

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

    /**
     * Converte este enum num objeto ServerInfo (POJO) para o MongoDB.
     */
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
                .playerCount(0)
                .build();
    }
}