package com.realmmc.controller.modules.server.data;

import lombok.Getter;

@Getter
public enum DefaultServer {

    // Formato: (name, displayName, ip, port, pterodactylId, maxPlayers, maxPlayersVip, minGroup, type)
    LOGIN("login-1", "Login", "181.214.48.24", 25566, "67827339-e73d-48de-823c-8e23fc4c41ff", 500, 500, "default", ServerType.LOGIN),
    LOBBY("lobby-1", "Lobby 1", "181.214.48.24", 25567, "680712e1-d63f-4c91-a8ef-68324783185c", 100, 150, "default", ServerType.LOBBY),
    BUILD("build-1", "Construção", "181.214.48.24", 25568, "b11fdc52-3cbe-4529-b3f0-776915fae3b8", 100, 100, "administrator", ServerType.PERSISTENT);

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
                .playerCount(0)
                .build();
    }
}