package com.palacesky.controller.modules.server.data;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ServerInfoRepository extends AbstractMongoRepository<ServerInfo> {

    public ServerInfoRepository() {
        super(ServerInfo.class, "servers");
        ensureIndexes();
    }

    private void ensureIndexes() {
        // Indexa o tipo de servidor e o status para pesquisas rápidas de scaling
        collection().createIndex(Indexes.ascending("type"));
        collection().createIndex(Indexes.ascending("status"));
        collection().createIndex(Indexes.compoundIndex(
                Indexes.ascending("type"),
                Indexes.ascending("status")
        ));
    }

    /**
     * Encontra um servidor pelo seu nome (ID).
     * @param name O _id (ex: "lobby-1")
     */
    public Optional<ServerInfo> findByName(String name) {
        return findOne(Filters.eq("_id", name));
    }

    /**
     * Encontra todos os servidores de um determinado tipo.
     */
    public List<ServerInfo> findByType(ServerType type) {
        List<ServerInfo> results = new ArrayList<>();
        collection().find(Filters.eq("type", type)).forEach(results::add);
        return results;
    }

    /**
     * Encontra todos os servidores de um tipo e status específicos.
     * (Ex: encontrar lobbies OFFLINE para ligar)
     */
    public List<ServerInfo> findByTypeAndStatus(ServerType type, ServerStatus status) {
        Bson filter = Filters.and(
                Filters.eq("type", type),
                Filters.eq("status", status)
        );
        List<ServerInfo> results = new ArrayList<>();
        collection().find(filter).forEach(results::add);
        return results;
    }

    /**
     * Guarda ou atualiza um servidor na base de dados.
     */
    public void save(ServerInfo serverInfo) {
        // Usa o 'name' (que é o @BsonId) como filtro
        replace(Filters.eq("_id", serverInfo.getName()), serverInfo);
    }
}