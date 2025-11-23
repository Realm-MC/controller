package com.realmmc.controller.modules.server.data;

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

    /** O nome único do servidor (ex: "lobby-1", "rankup-1") */
    @BsonId
    private String name;

    /** O ID alfanumérico (UUID/short ID) usado pela API de CLIENTE (ex: "d79f737e") */
    private String pterodactylId;

    /** O ID numérico INTERNO usado pela API de APLICAÇÃO (ex: 42) */
    private int internalPteroId;

    /** O ID alfanumérico do servidor no painel Pterodactyl (ex: "e0d9ff") */
    private String displayName;

    /** O endereço IP do nó (Node) que hospeda este servidor. */
    private String ip;

    /** A porta principal (Primary Port) atribuída pelo Pterodactyl. */
    private int port;

    /** O tipo de servidor (LOBBY, GAME_SW, PERSISTENT, etc.) */
    @Builder.Default
    private ServerType type = ServerType.PERSISTENT;

    /** O estado atual do servidor (ONLINE, OFFLINE, STARTING) */
    @Builder.Default
    private ServerStatus status = ServerStatus.OFFLINE;

    /** O número máximo de jogadores normais. */
    private int maxPlayers;

    /** O número máximo de jogadores VIP (slots de reserva). */
    private int maxPlayersVip;

    /** O nome do grupo (Role) mínimo para entrar. */
    @Builder.Default
    private String minGroup = "default";

    /** A contagem atual de jogadores neste servidor. */
    @Builder.Default
    private int playerCount = 0;
}