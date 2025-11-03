package com.realmmc.controller.modules.preferences;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.preferences.PreferencesSyncSubscriber;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class PreferencesModule extends AbstractCoreModule {

    private PreferencesSyncSubscriber syncSubscriber;
    private RedisSubscriber redisSubscriber;

    public PreferencesModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Preferences";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo para gestão de preferências dos jogadores.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Profile", "Database"};
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[PreferencesModule] Initializing preferences service...");
        PreferencesService preferencesService = new PreferencesService();
        ServiceRegistry.getInstance().registerService(PreferencesService.class, preferencesService);
        logger.info("[PreferencesModule] Preferences service initialized and registered.");

        try {
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            syncSubscriber = new PreferencesSyncSubscriber(this.redisSubscriber);
            syncSubscriber.startListening();
            logger.info("[PreferencesModule] PreferencesSyncSubscriber registered with shared subscriber.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[PreferencesModule] Failed to register PreferencesSyncSubscriber: RedisSubscriber not found!", e);
            this.redisSubscriber = null;
            this.syncSubscriber = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PreferencesModule] Unexpected error registering PreferencesSyncSubscriber!", e);
            this.redisSubscriber = null;
            this.syncSubscriber = null;
        }
    }

    @Override
    protected void onDisable() throws Exception {
        if (this.syncSubscriber != null && this.redisSubscriber != null) {
            try {
                this.redisSubscriber.unregisterListener(RedisChannel.PREFERENCES_SYNC);
                logger.info("[PreferencesModule] PreferencesSyncSubscriber unregistered from shared subscriber.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PreferencesModule] Error unregistering PreferencesSyncSubscriber.", e);
            }
        }
        this.syncSubscriber = null;
        this.redisSubscriber = null;

        ServiceRegistry.getInstance().unregisterService(PreferencesService.class);
        logger.info("[PreferencesModule] Preferences service finalized.");
    }
}