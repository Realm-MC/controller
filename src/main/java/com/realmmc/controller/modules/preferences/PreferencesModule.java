package com.realmmc.controller.modules.preferences;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.preferences.PreferencesSyncSubscriber;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class PreferencesModule extends AbstractCoreModule {

    private PreferencesSyncSubscriber syncSubscriber;

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
        return new String[]{"Profile"};
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando serviço de preferências...");
        PreferencesService preferencesService = new PreferencesService();
        ServiceRegistry.getInstance().registerService(PreferencesService.class, preferencesService);
        logger.info("Serviço de preferências inicializado e registrado.");

        syncSubscriber = new PreferencesSyncSubscriber(new RedisSubscriber());
        syncSubscriber.startListening();
    }

    @Override
    protected void onDisable() throws Exception {
        if (syncSubscriber != null) {
            syncSubscriber.stopListening();
        }
        ServiceRegistry.getInstance().unregisterService(PreferencesService.class);
        logger.info("Serviço de preferências finalizado.");
    }
}