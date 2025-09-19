package com.realmmc.controller.modules.profile;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.profile.ProfileSyncSubscriber;
import java.util.logging.Logger;

public class ProfileModule extends AbstractCoreModule {
    private ProfileService profileService;
    private ProfileSyncSubscriber profileSyncSubscriber;
    
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
        logger.info("Inicializando serviço de perfis...");
        
        profileService = new ProfileService();
        ServiceRegistry.getInstance().registerService(ProfileService.class, profileService);
        
        profileSyncSubscriber = new ProfileSyncSubscriber();
        profileSyncSubscriber.start();
        
        logger.info("Módulo de perfis inicializado com sucesso");
    }
    
    @Override
    protected void onDisable() throws Exception {
        logger.info("Finalizando serviço de perfis...");
        
        if (profileSyncSubscriber != null) {
            profileSyncSubscriber.stop();
        }
        
        ServiceRegistry.getInstance().unregisterService(ProfileService.class);
        
        logger.info("Módulo de perfis finalizado");
    }
    
    @Override
    public String[] getDependencies() {
        return new String[]{"Database"};
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
}