package com.realmmc.controller.shared.profile;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.realmmc.controller.shared.storage.mongodb.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public class ProfileRepository extends AbstractMongoRepository<Profile> {

    public ProfileRepository() {
        super(Profile.class, "profiles");
        ensureIndexes();
    }

    private void ensureIndexes() {
        MongoCollection<Profile> col = collection();
        col.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true));
        col.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
        col.createIndex(Indexes.descending("lastLogin"));
    }

    public Optional<Profile> findByUuid(UUID uuid) {
        return findOne(Filters.eq("_id", uuid));
    }

    public Optional<Profile> findByName(String name) {
        return findOne(Filters.eq("name", name));
    }

    public Optional<Profile> findByUsername(String username) {
        return findOne(Filters.eq("username", username));
    }

    public void upsert(Profile profile) {
        if (profile.getId() == null) throw new IllegalArgumentException("Profile id cannot be null");
        replace(MongoRepository.idEquals(profile.getId()), profile);
    }

    public void deleteByUuid(UUID uuid) {
        delete(Filters.eq("_id", uuid));
    }
}
