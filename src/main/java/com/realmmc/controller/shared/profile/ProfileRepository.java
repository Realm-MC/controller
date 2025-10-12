package com.realmmc.controller.shared.profile;

import com.mongodb.client.model.Filters;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class ProfileRepository extends AbstractMongoRepository<Profile> {

    public ProfileRepository() {
        super(Profile.class, "profiles");
    }

    public Optional<Profile> findById(int id) {
        return findOne(Filters.eq("_id", id));
    }

    public Optional<Profile> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    public Optional<Profile> findByName(String name) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(name) + "$", Pattern.CASE_INSENSITIVE);
        return findOne(Filters.regex("name", pattern));
    }

    public Optional<Profile> findByUsername(String username) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(username) + "$", Pattern.CASE_INSENSITIVE);
        return findOne(Filters.regex("username", pattern));
    }

    public List<Profile> findByRoleId(int roleId) {
        List<Profile> profiles = new ArrayList<>();
        collection().find(Filters.eq("roleIds", roleId)).into(profiles);
        return profiles;
    }

    public void upsert(Profile profile) {
        replace(Filters.eq("_id", profile.getId()), profile);
    }

    public void deleteByUuid(UUID uuid) {
        delete(Filters.eq("uuid", uuid));
    }
}