package com.palacesky.controller.shared.stats;

import com.palacesky.controller.shared.profile.Profile;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class StatisticsService {

    private final StatisticsRepository repository = new StatisticsRepository();
    private static final Logger LOGGER = Logger.getLogger(StatisticsService.class.getName());

    public Statistics ensureStatistics(Profile profile) {
        return repository.findById(profile.getId()).orElseGet(() -> {
            Statistics newStats = Statistics.builder()
                    .id(profile.getId())
                    .uuid(profile.getUuid())
                    .name(profile.getName())
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

        Optional<Statistics> statsOpt = repository.findByUuid(uuid);
        if (statsOpt.isPresent()) {
            Statistics stats = statsOpt.get();
            stats.setOnlineTime(stats.getOnlineTime() + sessionMillis);
            repository.upsert(stats);
        } else {
            LOGGER.warning("Tentativa de adicionar tempo online para o UUID " + uuid + ", mas o registo de estatísticas não foi encontrado!");
        }
    }

    public void updateIdentification(Profile profile) {
        Statistics stats = ensureStatistics(profile);
        boolean changed = false;

        if (profile.getName() != null && !profile.getName().equals(stats.getName())) {
            stats.setName(profile.getName());
            changed = true;
        }

        if (changed) {
            repository.upsert(stats);
        }
    }
}