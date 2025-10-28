package com.realmmc.controller.shared.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /**
     * O ID único do grupo, ex: "master".
     * Usamos o nome como ID para facilitar as pesquisas.
     */
    @BsonId
    private String name;

    /**
     * O nome de exibição formatado (com cor), ex: "<gold>Master".
     */
    private String displayName;

    /**
     * O prefixo de chat do grupo, ex: "<gold>[Master] ".
     */
    private String prefix;

    /**
     * O sufixo de chat do grupo, ex: "<gold>[EQUIPE]".
     */
    private String suffix;

    /**
     * A cor principal do grupo, ex: "<gold>".
     */
    private String color;

    /**
     * A categoria do grupo (STAFF, VIP, DEFAULT).
     */
    private RoleType type;

    /**
     * O peso (prioridade) do grupo. Usado para determinar o grupo primário.
     */
    private int weight;

    /**
     * Lista de permissões que este grupo concede.
     */
    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    /**
     * Lista de nomes (IDs) de outros grupos que este herda.
     * Ex: ["administrator"]
     */
    @Builder.Default
    private List<String> inheritance = new ArrayList<>();

    /**
     * Data de criação do registo.
     */
    private long createdAt;

    /**
     * Data da última atualização do registo.
     */
    private long updatedAt;
}