package com.realmmc.controller.modules.profile;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.profile.ProfileSyncSubscriber; // Importa o novo listener
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber; // Importa o subscriber
// --- Adicionar import ---
import com.realmmc.controller.shared.session.SessionTrackerService;
// --- Fim Adição ---
import java.util.logging.Level; // Import Level
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class ProfileModule extends AbstractCoreModule {

    private ProfileSyncSubscriber profileSyncSubscriber; // <<< Tipo mudado para o novo listener
    private RedisSubscriber redisSubscriber; // <<< Referência ao subscriber compartilhado
    // --- Adicionar instância ---
    private SessionTrackerService sessionTrackerService;
    // --- Fim Adição ---

    public ProfileModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Profile";
    }

    // <<< ADICIONADO MÉTODO getVersion() >>>
    @Override
    public String getVersion() {
        return "1.0.0"; // Ou a versão correta
    }
    // <<< FIM ADIÇÃO >>>

    @Override
    public String getDescription() {
        return "Módulo de gerenciamento de perfis de jogadores";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando serviço de perfis e rastreamento de sessão..."); // Mensagem atualizada

        // --- Inicializa ProfileService ---
        ProfileService profileService = new ProfileService();
        ServiceRegistry.getInstance().registerService(ProfileService.class, profileService);

        // --- Inicializa SessionTrackerService ---
        try {
            this.sessionTrackerService = new SessionTrackerService(); // Cria a instância
            ServiceRegistry.getInstance().registerService(SessionTrackerService.class, this.sessionTrackerService); // Registra
            logger.info("SessionTrackerService inicializado e registrado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha crítica ao inicializar SessionTrackerService (dependência ProfileService ausente?). Módulo Profile não habilitado completamente.", e);
            // Não lançar exceção aqui permite que ProfileService funcione, mas SessionTracker não.
            this.sessionTrackerService = null; // Garante que é nulo
            // Poderia lançar 'throw e;' se SessionTracker for absolutamente essencial
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao inicializar SessionTrackerService!", e);
            this.sessionTrackerService = null;
        }
        // --- Fim SessionTrackerService ---

        // --- Lógica do ProfileSyncSubscriber (sem mudanças) ---
        try {
            // Obtém o subscriber compartilhado (criado pelo DatabaseModule)
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);

            // Instancia o novo listener (que agora é só um listener)
            this.profileSyncSubscriber = new ProfileSyncSubscriber();

            // Registra o listener no subscriber compartilhado
            this.redisSubscriber.registerListener(RedisChannel.PROFILES_SYNC, this.profileSyncSubscriber);

            logger.info("ProfileSyncSubscriber (v2) registrado no RedisSubscriber compartilhado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao registrar ProfileSyncSubscriber: RedisSubscriber não encontrado!", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao registrar ProfileSyncSubscriber!", e);
            this.redisSubscriber = null;
            this.profileSyncSubscriber = null;
        }
        // <<< FIM ATUALIZAÇÃO >>>

        logger.info("Módulo de perfis inicializado"); // Mensagem original ligeiramente ajustada
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("Finalizando serviço de perfis e rastreamento de sessão..."); // Mensagem atualizada

        // <<< LÓGICA DE DESREGISTRO ATUALIZADA >>>
        if (this.profileSyncSubscriber != null && this.redisSubscriber != null) {
            try {
                this.redisSubscriber.unregisterListener(RedisChannel.PROFILES_SYNC);
                logger.info("ProfileSyncSubscriber (v2) desregistrado.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao desregistrar ProfileSyncSubscriber.", e);
            }
        }
        this.profileSyncSubscriber = null;
        this.redisSubscriber = null;
        // <<< FIM ATUALIZAÇÃO >>>

        // --- Desregistro do SessionTrackerService ---
        if (this.sessionTrackerService != null) {
            ServiceRegistry.getInstance().unregisterService(SessionTrackerService.class);
            logger.info("SessionTrackerService desregistrado.");
            this.sessionTrackerService = null;
        }
        // --- Fim Desregistro ---

        // --- Desregistro do ProfileService (sem mudanças) ---
        ServiceRegistry.getInstance().unregisterService(ProfileService.class);

        logger.info("Módulo de perfis finalizado");
    }

    @Override
    public String[] getDependencies() {
        // Depende de Database (para MongoDB e RedisSubscriber) e Scheduler (para RoleService)
        return new String[]{"Database", "SchedulerModule"};
    }

    @Override
    public int getPriority() {
        return 18; // Roda depois de Database e Scheduler
    }
}