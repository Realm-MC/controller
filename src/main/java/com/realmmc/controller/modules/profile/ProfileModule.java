package com.realmmc.controller.modules.profile;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.profile.ProfileSyncSubscriber;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.shared.session.SessionTrackerService;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class ProfileModule extends AbstractCoreModule {

    private ProfileSyncSubscriber profileSyncSubscriber;
    private RedisSubscriber redisSubscriber;
    private SessionTrackerService sessionTrackerService;

    public ProfileModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Profile";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo de gerenciamento de perfis de jogadores";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[ProfileModule] Initializing profile and session tracking service...");

        ProfileService profileService = new ProfileService();
        ServiceRegistry.getInstance().registerService(ProfileService.class, profileService);

        try {
            this.sessionTrackerService = new SessionTrackerService();
            ServiceRegistry.getInstance().registerService(SessionTrackerService.class, this.sessionTrackerService);
            logger.info("[ProfileModule] SessionTrackerService initialized and registered.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[ProfileModule] Critical failure initializing SessionTrackerService. Profile module not fully enabled.", e);
            this.sessionTrackerService = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ProfileModule] Unexpected error initializing SessionTrackerService!", e);
            this.sessionTrackerService = null;
        }

        try {
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);

            this.profileSyncSubscriber = new ProfileSyncSubscriber();

            this.redisSubscriber.registerListener(RedisChannel.PROFILES_SYNC, this.profileSyncSubscriber);

            logger.info("[ProfileModule] ProfileSyncSubscriber (v2) registered with shared RedisSubscriber.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[ProfileModule] Failed to register ProfileSyncSubscriber: RedisSubscriber not found!", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ProfileModule] Unexpected error registering ProfileSyncSubscriber!", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        }

        logger.info("[ProfileModule] Profile module initialized");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("[ProfileModule] Finalizing profile and session tracking service...");

        if (this.profileSyncSubscriber != null && this.redisSubscriber != null) {
            try {
                this.redisSubscriber.unregisterListener(RedisChannel.PROFILES_SYNC);
                logger.info("[ProfileModule] ProfileSyncSubscriber (v2) unregistered.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ProfileModule] Error unregistering ProfileSyncSubscriber.", e);
            }
        }
        this.profileSyncSubscriber = null;
        this.redisSubscriber = null;

        if (this.sessionTrackerService != null) {
            ServiceRegistry.getInstance().unregisterService(SessionTrackerService.class);
            logger.info("[ProfileModule] SessionTrackerService unregistered.");
            this.sessionTrackerService = null;
        }

        ServiceRegistry.getInstance().unregisterService(ProfileService.class);

        logger.info("[ProfileModule] Profile module finalized");
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Database", "SchedulerModule"};
    }

    @Override
    public int getPriority() {
        return 18;
    }
}