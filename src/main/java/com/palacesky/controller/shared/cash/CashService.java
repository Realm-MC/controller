package com.palacesky.controller.shared.cash;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.utils.TaskScheduler;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CashService {

    private static final Logger LOGGER = Logger.getLogger(CashService.class.getName());
    private final ProfileService profileService;
    private volatile List<Profile> cachedTop10 = new ArrayList<>();

    @Getter
    private long nextUpdateTimestamp = 0L;
    private static final int UPDATE_INTERVAL_MINUTES = 5;

    public CashService() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
    }

    public void startCacheTask() {
        TaskScheduler.runAsyncTimer(this::updateCache, 1, UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES);
        this.nextUpdateTimestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1);
        LOGGER.info("Tarefa de cache do Top 10 Cash iniciada.");
    }

    public void updateCache() {
        try {
            LOGGER.fine("Atualizando cache do Top 10 Cash...");
            this.cachedTop10 = profileService.getTopCash(10);

            this.nextUpdateTimestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(UPDATE_INTERVAL_MINUTES);

            LOGGER.fine("Cache do Top 10 Cash atualizado.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao atualizar o cache do Top 10 Cash", e);
        }
    }

    public List<Profile> getCachedTop10() {
        return Collections.unmodifiableList(this.cachedTop10);
    }
}