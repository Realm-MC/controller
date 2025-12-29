package com.palacesky.controller.modules.motd.data;

import com.mongodb.client.model.Filters;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.palacesky.controller.shared.storage.mongodb.MongoRepository;

import java.util.Optional;

public class MotdRepository extends AbstractMongoRepository<MotdData> {

    public MotdRepository() {
        super(MotdData.class, "motd_config");
    }

    public Optional<MotdData> getGlobalMotd() {
        return findOne(Filters.eq("_id", "global_motd"));
    }

    public void save(MotdData data) {
        replace(MongoRepository.idEquals("global_motd"), data);
    }
}