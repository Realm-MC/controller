package com.realmmc.controller.modules.preferences;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.preferences.PreferencesSyncSubscriber;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber; // Importar o Subscriber
import java.util.logging.Level; // Importar Level
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class PreferencesModule extends AbstractCoreModule {

    private PreferencesSyncSubscriber syncSubscriber;
    // <<< CORREÇÃO: Adicionar referência ao subscriber compartilhado >>>
    private RedisSubscriber redisSubscriber;

    public PreferencesModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Preferences"; // Nome correto (sem "Module")
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
        // <<< CORREÇÃO: Adicionar dependência do DatabaseModule (que provê o RedisSubscriber) >>>
        return new String[]{"Profile", "Database"};
    }

    @Override
    public int getPriority() {
        return 25; // Prioridade OK (depois de Profile 18 e Database 10)
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando serviço de preferências...");
        PreferencesService preferencesService = new PreferencesService();
        ServiceRegistry.getInstance().registerService(PreferencesService.class, preferencesService);
        logger.info("Serviço de preferências inicializado e registrado.");

        // <<< CORREÇÃO: Usar o RedisSubscriber compartilhado >>>
        try {
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            // Passa o subscriber compartilhado para o construtor do listener
            syncSubscriber = new PreferencesSyncSubscriber(this.redisSubscriber);
            syncSubscriber.startListening(); // Registra o listener
            logger.info("PreferencesSyncSubscriber registrado no subscriber compartilhado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao registrar PreferencesSyncSubscriber: RedisSubscriber não encontrado!", e);
            this.redisSubscriber = null;
            this.syncSubscriber = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao registrar PreferencesSyncSubscriber!", e);
            this.redisSubscriber = null;
            this.syncSubscriber = null;
        }
        // <<< FIM CORREÇÃO >>>
    }

    @Override
    protected void onDisable() throws Exception {
        if (syncSubscriber != null) {
            syncSubscriber.stopListening(); // Desregistra o listener
        }
        // Limpa referências
        this.syncSubscriber = null;
        this.redisSubscriber = null;

        ServiceRegistry.getInstance().unregisterService(PreferencesService.class);
        logger.info("Serviço de preferências finalizado.");
    }
}