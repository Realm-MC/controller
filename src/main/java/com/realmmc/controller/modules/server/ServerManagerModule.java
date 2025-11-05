package com.realmmc.controller.modules.server;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Módulo responsável por gerir o ciclo de vida e registo
 * de servidores (Lobbies, Minigames) via Pterodactyl.
 * Este módulo deve correr APENAS NO PROXY (VELOCITY).
 */
@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class ServerManagerModule extends AbstractCoreModule {

    private PterodactylService pterodactylService;
    private ServerRegistryService serverRegistryService;

    public ServerManagerModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "ServerManager"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Gestor de servidores dinâmicos (Pterodactyl Auto-Scaling)."; }

    @Override public String[] getDependencies() {
        // <<< CORREÇÃO: Dependência circular removida >>>
        // Precisa apenas do DB e do Scheduler. O ProxyServer.class é injetado pelo Proxy.java (main).
        return new String[]{"Database", "SchedulerModule"};
    }

    // <<< CORREÇÃO: Prioridade 40 (para carregar ANTES do ProxyModule) >>>
    @Override public int getPriority() { return 40; }

    @Override
    protected void onEnable() throws Exception {
        try {
            // 1. Regista o PterodactylService
            this.pterodactylService = new PterodactylService(logger);
            ServiceRegistry.getInstance().registerService(PterodactylService.class, this.pterodactylService);
            logger.info("PterodactylService registado.");

            // 2. Regista e inicializa o ServerRegistryService (o cérebro)
            this.serverRegistryService = new ServerRegistryService(logger);
            ServiceRegistry.getInstance().registerService(ServerRegistryService.class, this.serverRegistryService);
            logger.info("ServerRegistryService registado.");

            // 3. Inicia a lógica do serviço
            this.serverRegistryService.initialize();

            logger.info("Módulo ServerManager iniciado com sucesso.");

        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao iniciar ServerManagerModule: " + e.getMessage());
            this.pterodactylService = null;
            this.serverRegistryService = null;
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro crítico ao iniciar ServerManagerModule.", e);
            this.pterodactylService = null;
            this.serverRegistryService = null;
            throw e;
        }
    }

    @Override
    protected void onDisable() throws Exception {
        // 1. Finaliza o ServerRegistryService (para a tarefa de monitorização)
        if (this.serverRegistryService != null) {
            this.serverRegistryService.shutdown();
            ServiceRegistry.getInstance().unregisterService(ServerRegistryService.class);
            logger.info("ServerRegistryService finalizado e desregistado.");
        }

        // 2. Desregista o PterodactylService
        if (this.pterodactylService != null) {
            ServiceRegistry.getInstance().unregisterService(PterodactylService.class);
            logger.info("PterodactylService desregistado.");
        }

        this.serverRegistryService = null;
        this.pterodactylService = null;
        logger.info("Módulo ServerManager finalizado.");
    }
}