package com.palacesky.controller.modules.profile;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.cosmetics.CosmeticsService;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.profile.ProfileSyncSubscriber;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import com.palacesky.controller.shared.session.SessionTrackerService;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class ProfileModule extends AbstractCoreModule {

    private ProfileSyncSubscriber profileSyncSubscriber;
    private RedisSubscriber redisSubscriber;
    private SessionTrackerService sessionTrackerService;
    private CosmeticsService cosmeticsService;

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
        return "Módulo de gerenciamento de perfis, sessões e cosméticos";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[ProfileModule] Inicializando serviços de perfil, sessão e cosméticos...");

        ProfileService profileService = new ProfileService();
        ServiceRegistry.getInstance().registerService(ProfileService.class, profileService);

        try {
            this.sessionTrackerService = new SessionTrackerService();
            ServiceRegistry.getInstance().registerService(SessionTrackerService.class, this.sessionTrackerService);
            logger.info("[ProfileModule] SessionTrackerService inicializado e registrado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[ProfileModule] Falha crítica ao inicializar SessionTrackerService.", e);
            this.sessionTrackerService = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ProfileModule] Erro inesperado ao inicializar SessionTrackerService.", e);
            this.sessionTrackerService = null;
        }

        try {
            this.cosmeticsService = new CosmeticsService();
            ServiceRegistry.getInstance().registerService(CosmeticsService.class, this.cosmeticsService);
            logger.info("[ProfileModule] CosmeticsService inicializado e registrado.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ProfileModule] Erro ao inicializar CosmeticsService.", e);
            this.cosmeticsService = null;
        }

        try {
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            this.profileSyncSubscriber = new ProfileSyncSubscriber();
            this.redisSubscriber.registerListener(RedisChannel.PROFILES_SYNC, this.profileSyncSubscriber);
            logger.info("[ProfileModule] ProfileSyncSubscriber registrado com o RedisSubscriber compartilhado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[ProfileModule] Falha ao registrar ProfileSyncSubscriber: RedisSubscriber não encontrado.", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ProfileModule] Erro inesperado ao registrar ProfileSyncSubscriber.", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        }

        logger.info("[ProfileModule] Módulo de perfil habilitado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("[ProfileModule] Finalizando serviços de perfil e sessão...");

        if (this.profileSyncSubscriber != null && this.redisSubscriber != null) {
            try {
                this.redisSubscriber.unregisterListener(RedisChannel.PROFILES_SYNC);
                logger.info("[ProfileModule] ProfileSyncSubscriber desregistrado.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "[ProfileModule] Erro ao desregistrar ProfileSyncSubscriber.", e);
            }
        }
        this.profileSyncSubscriber = null;
        this.redisSubscriber = null;

        if (this.cosmeticsService != null) {
            ServiceRegistry.getInstance().unregisterService(CosmeticsService.class);
            logger.info("[ProfileModule] CosmeticsService desregistrado.");
            this.cosmeticsService = null;
        }

        if (this.sessionTrackerService != null) {
            ServiceRegistry.getInstance().unregisterService(SessionTrackerService.class);
            logger.info("[ProfileModule] SessionTrackerService desregistrado.");
            this.sessionTrackerService = null;
        }

        ServiceRegistry.getInstance().unregisterService(ProfileService.class);

        logger.info("[ProfileModule] Módulo de perfil finalizado.");
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