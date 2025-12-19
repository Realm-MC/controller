package com.palacesky.controller.shared.role;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;

import java.util.Optional;

public class RoleRepository extends AbstractMongoRepository<Role> {

    public RoleRepository() {
        super(Role.class, "roles");
        ensureIndexes();
    }

    private void ensureIndexes() {
        // O índice _id (name) já é único por defeito.
        // Vamos criar um índice no 'weight' para consultas de ordenação rápidas.
        collection().createIndex(Indexes.descending("weight"));
    }

    /**
     * Encontra um grupo pelo seu nome (ID).
     * @param name O ID do grupo (ex: "master")
     */
    public Optional<Role> findByName(String name) {
        // Como o 'name' é o @BsonId, podemos usar o filtro _id
        return findOne(Filters.eq("_id", name));
    }

    /**
     * Guarda ou atualiza um grupo na base de dados.
     */
    public void upsert(Role role) {
        role.setUpdatedAt(System.currentTimeMillis());
        if (role.getCreatedAt() == 0) {
            role.setCreatedAt(System.currentTimeMillis());
        }
        // Usa o 'name' (que é o @BsonId) como filtro para o upsert
        replace(Filters.eq("_id", role.getName()), role);
    }
}