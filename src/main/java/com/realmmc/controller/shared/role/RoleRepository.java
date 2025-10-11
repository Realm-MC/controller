package com.realmmc.controller.shared.role;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoleRepository extends AbstractMongoRepository<Role> {

    public RoleRepository() {
        super(Role.class, "roles");
    }

    public Optional<Role> findById(int id) {
        return findOne(Filters.eq("_id", id));
    }

    public Optional<Role> findByName(String name) {
        return findOne(Filters.eq("name", name));
    }

    public List<Role> findAll() {
        List<Role> roles = new ArrayList<>();
        collection().find().into(roles);
        return roles;
    }

    public List<Role> findAllOrderedByWeight() {
        List<Role> roles = new ArrayList<>();
        collection().find().sort(Sorts.descending("weight")).into(roles);
        return roles;
    }

    public void upsert(Role role) {
        replace(Filters.eq("_id", role.getId()), role);
    }

    public void deleteById(int id) {
        delete(Filters.eq("_id", id));
    }
}