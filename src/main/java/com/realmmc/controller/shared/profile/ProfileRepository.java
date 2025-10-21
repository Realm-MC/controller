package com.realmmc.controller.shared.profile;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
// NOVO IMPORT para Collation e CollationStrength
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
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
        col.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));

        col.createIndex(Indexes.ascending("username"), new IndexOptions()
                .unique(true)
                .collation(Collation.builder()
                        .locale("en")
                        .collationStrength(CollationStrength.SECONDARY)
                        .build()));

        col.createIndex(Indexes.descending("lastLogin"));
    }

    public Optional<Profile> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    /**
     * Finds a profile by its numeric ID (_id field).
     * @param id The numeric ID.
     * @return Optional containing the profile if found.
     */
    public Optional<Profile> findById(int id) {
        return findOne(MongoRepository.idEquals(id));
    }

    /**
     * Finds a profile by its display name (case-sensitive by default in MongoDB).
     * @param name The display name.
     * @return Optional containing the profile if found.
     */
    public Optional<Profile> findByName(String name) {
        return findOne(Filters.eq("name", name));
    }

    /**
     * Finds a profile by its username (search should be case-insensitive due to index collation).
     * @param username The username.
     * @return Optional containing the profile if found.
     */
    public Optional<Profile> findByUsername(String username) {
        return findOne(Filters.eq("username", username));
    }

    public void upsert(Profile profile) {
        if (profile.getId() == null) throw new IllegalArgumentException("Profile integer _id cannot be null for upsert");
        replace(MongoRepository.idEquals(profile.getId()), profile);
    }

    public void deleteByUuid(UUID uuid) {
        delete(Filters.eq("uuid", uuid));
    }
}