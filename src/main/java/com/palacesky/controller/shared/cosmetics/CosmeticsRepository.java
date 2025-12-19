package com.palacesky.controller.shared.cosmetics;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.palacesky.controller.shared.storage.mongodb.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public class CosmeticsRepository extends AbstractMongoRepository<Cosmetics> {

    public CosmeticsRepository() {
        super(Cosmetics.class, "cosmetics");
        ensureIndexes();
    }

    private void ensureIndexes() {
        collection().createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
    }

    public Optional<Cosmetics> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    public void upsert(Cosmetics cosmetics) {
        if (cosmetics.getId() == null) throw new IllegalArgumentException("Cosmetics ID cannot be null");
        replace(MongoRepository.idEquals(cosmetics.getId()), cosmetics);
    }
}