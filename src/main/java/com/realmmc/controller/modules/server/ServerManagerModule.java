package com.realmmc.controller.modules.server;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.listeners.ServerStatusListener;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class ServerManagerModule extends AbstractCoreModule {

    private PterodactylService pterodactylService;
    private ServerRegistryService serverRegistryService;
    private ServerTemplateManager serverTemplateManager;
    private ServerStatusListener statusListener;

    public ServerManagerModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "ServerManager"; }
    @Override public String getVersion() { return "1.1.0"; }
    @Override public String getDescription() { return "Gestor de servidores dinâmicos (Auto-Scaling & Templates)."; }

    @Override public String[] getDependencies() {
        return new String[]{"Database", "SchedulerModule"};
    }

    @Override public int getPriority() { return 40; }

    @Override
    protected void onEnable() throws Exception {
        try {
            File dataFolder = new File("plugins/controller");
            if (!dataFolder.exists()) dataFolder.mkdirs();

            this.serverTemplateManager = new ServerTemplateManager(dataFolder, logger);
            this.serverTemplateManager.load();
            ServiceRegistry.getInstance().registerService(ServerTemplateManager.class, this.serverTemplateManager);
            logger.info("ServerTemplateManager registado e carregado.");

            this.pterodactylService = new PterodactylService(logger);
            ServiceRegistry.getInstance().registerService(PterodactylService.class, this.pterodactylService);
            logger.info("PterodactylService registado.");

            this.serverRegistryService = new ServerRegistryService(logger);
            ServiceRegistry.getInstance().registerService(ServerRegistryService.class, this.serverRegistryService);
            logger.info("ServerRegistryService registado.");

            this.serverRegistryService.initialize();

            try {
                RedisSubscriber redis = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
                this.statusListener = new ServerStatusListener();
                redis.registerListener(RedisChannel.SERVER_STATUS_UPDATE, this.statusListener);
                logger.info("ServerStatusListener (Redis) registado.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao registar ServerStatusListener", e);
            }

            logger.info("Módulo ServerManager iniciado com sucesso.");

        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao iniciar ServerManagerModule: " + e.getMessage());
            shutdownServices();
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro crítico ao iniciar ServerManagerModule.", e);
            shutdownServices();
            throw e;
        }
    }

    @Override
    protected void onDisable() throws Exception {
        shutdownServices();
        logger.info("Módulo ServerManager finalizado.");
    }

    private void shutdownServices() {
        if (this.statusListener != null) {
            ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                    .ifPresent(r -> r.unregisterListener(RedisChannel.SERVER_STATUS_UPDATE));
        }

        if (this.serverRegistryService != null) {
            this.serverRegistryService.shutdown();
            ServiceRegistry.getInstance().unregisterService(ServerRegistryService.class);
        }

        if (this.pterodactylService != null) {
            ServiceRegistry.getInstance().unregisterService(PterodactylService.class);
        }

        if (this.serverTemplateManager != null) {
            ServiceRegistry.getInstance().unregisterService(ServerTemplateManager.class);
        }

        this.serverRegistryService = null;
        this.pterodactylService = null;
        this.serverTemplateManager = null;
        this.statusListener = null;
    }
}