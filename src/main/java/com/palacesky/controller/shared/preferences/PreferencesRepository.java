package com.palacesky.controller.shared.preferences;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.palacesky.controller.shared.storage.mongodb.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public class PreferencesRepository extends AbstractMongoRepository<Preferences> {

    public PreferencesRepository() {
        super(Preferences.class, "preferences");
        ensureIndexes();
    }

    private void ensureIndexes() {
        collection().createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
    }

    public Optional<Preferences> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    public Optional<Preferences> findById(int id) {
        return findOne(MongoRepository.idEquals(id));
    }

    public void upsert(Preferences preferences) {
        if (preferences.getId() == null) throw new IllegalArgumentException("Preferences integer _id cannot be null for upsert");
        replace(MongoRepository.idEquals(preferences.getId()), preferences);
    }
}