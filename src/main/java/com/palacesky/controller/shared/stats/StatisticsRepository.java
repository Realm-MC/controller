package com.palacesky.controller.shared.stats;

import com.mongodb.client.model.Filters;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.palacesky.controller.shared.storage.mongodb.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public class StatisticsRepository extends AbstractMongoRepository<Statistics> {

    public StatisticsRepository() {
        super(Statistics.class, "statistics");
    }

    public Optional<Statistics> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    public Optional<Statistics> findById(int id) {
        return findOne(MongoRepository.idEquals(id));
    }

    public void upsert(Statistics stats) {
        if (stats.getId() == null) {
            throw new IllegalArgumentException("O ID das estatísticas não pode ser nulo para o upsert.");
        }
        replace(MongoRepository.idEquals(stats.getId()), stats);
    }
}