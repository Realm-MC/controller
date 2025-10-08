package com.realmmc.controller.shared.stats;

import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.storage.mongodb.MongoRepository;
import org.bson.conversions.Bson;

import java.util.Optional;
import java.util.UUID;

public class StatisticsService {

    private final StatisticsRepository repository = new StatisticsRepository();

    public Statistics ensureStatistics(Profile profile) {
        return repository.findById(profile.getId()).orElseGet(() -> {
            Statistics newStats = Statistics.builder()
                    .id(profile.getId())
                    .uuid(profile.getUuid())
                    .name(profile.getName())
                    .username(profile.getUsername())
                    .build();
            repository.upsert(newStats);
            return newStats;
        });
    }

    public Optional<Statistics> getStatistics(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    public void addOnlineTime(UUID uuid, long sessionMillis) {
        if (sessionMillis <= 0) return;

        repository.findByUuid(uuid).ifPresent(stats -> {
            stats.setOnlineTime(stats.getOnlineTime() + sessionMillis);
            repository.upsert(stats);
        });
    }

    public void updateIdentification(Profile profile) {
        Statistics stats = ensureStatistics(profile);
        boolean changed = false;
        if (!profile.getName().equals(stats.getName())) {
            stats.setName(profile.getName());
            changed = true;
        }
        if (profile.getUsername() != null && !profile.getUsername().equals(stats.getUsername())) {
            stats.setUsername(profile.getUsername());
            changed = true;
        }
        if (changed) {
            repository.upsert(stats);
        }
    }
}