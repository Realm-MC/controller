package com.realmmc.controller.modules.server;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.listeners.ServerStatusListener;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class ServerManagerModule extends AbstractCoreModule {

    private PterodactylService pterodactylService;
    private ServerRegistryService serverRegistryService;
    private ServerStatusListener statusListener;

    public ServerManagerModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "ServerManager"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Gestor de servidores dinâmicos (Pterodactyl Auto-Scaling)."; }

    @Override public String[] getDependencies() {
        return new String[]{"Database", "SchedulerModule"};
    }

    @Override public int getPriority() { return 40; }

    @Override
    protected void onEnable() throws Exception {
        try {
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
        if (this.statusListener != null) {
            ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                    .ifPresent(r -> r.unregisterListener(RedisChannel.SERVER_STATUS_UPDATE));
        }

        if (this.serverRegistryService != null) {
            this.serverRegistryService.shutdown();
            ServiceRegistry.getInstance().unregisterService(ServerRegistryService.class);
            logger.info("ServerRegistryService finalizado e desregistado.");
        }

        if (this.pterodactylService != null) {
            ServiceRegistry.getInstance().unregisterService(PterodactylService.class);
            logger.info("PterodactylService desregistado.");
        }

        this.serverRegistryService = null;
        this.pterodactylService = null;
        this.statusListener = null;
        logger.info("Módulo ServerManager finalizado.");
    }
}