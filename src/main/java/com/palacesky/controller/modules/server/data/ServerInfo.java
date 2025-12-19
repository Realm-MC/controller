package com.palacesky.controller.modules.server.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerInfo {

    /** O nome único do servidor (ex: "lobby-1", "bw-1") */
    @BsonId
    private String name;

    /** O ID alfanumérico (UUID/short ID) usado pela API de CLIENTE */
    private String pterodactylId;

    /** O ID numérico INTERNO usado pela API de APLICAÇÃO */
    private int internalPteroId;

    private String displayName;
    private String ip;
    private int port;

    @Builder.Default
    private ServerType type = ServerType.PERSISTENT;

    /** Estado operacional (ONLINE, OFFLINE, etc) - Nível Infraestrutura */
    @Builder.Default
    private ServerStatus status = ServerStatus.OFFLINE;

    /** Estado do Jogo (WAITING, IN_GAME) - Nível Aplicação */
    @Builder.Default
    private GameState gameState = GameState.UNKNOWN;

    /** Nome do mapa atual carregado (para Minigames) */
    private String mapName;

    /** Se o servidor pode ser desligado pelo auto-scaler.
     * False enquanto uma partida ocorre. */
    @Builder.Default
    private boolean canShutdown = true;

    private int maxPlayers;
    private int maxPlayersVip;

    @Builder.Default
    private String minGroup = "default";

    @Builder.Default
    private int playerCount = 0;
}