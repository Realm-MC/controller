package com.realmmc.controller.proxy;

import com.realmmc.controller.core.ControllerCore;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.proxy.ProxyModule;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(id = "controller", name = "Controller", description = "Core controller para RealmMC", version = "1.0.0", authors = {"onyell", "lucas"})
public class Proxy extends ControllerCore {

    @Getter
    private static Proxy instance;
    @Getter
    private final ProxyServer server;
    private final Path directory;
    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;

    @Inject
    public Proxy(ProxyServer server, Logger logger, @DataDirectory Path directory) {
        super(logger);
        this.server = server;
        this.directory = directory;
        instance = this;
    }

    @Override
    public void initialize() {
        logger.info("Inicializando Controller Core (Proxy)...");
        
        initializeSharedServices();
        
        serviceRegistry = new ServiceRegistry(logger);
        moduleManager = new ModuleManager(logger);
        
        moduleManager.registerModule(new DatabaseModule(logger));
        moduleManager.registerModule(new SchedulerModule(server, this, logger));
        moduleManager.registerModule(new ProfileModule(logger));
        moduleManager.registerModule(new CommandModule(logger));
        moduleManager.registerModule(new ProxyModule(server, this, logger));
        
        moduleManager.enableAllModules();
        
        logger.info("Controller Core (Proxy) inicializado com sucesso!");
    }
    
    @Override
    protected void initializeSharedServices() {
        super.initializeSharedServices();

        File messagesDir = new File(directory.toFile(), "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        
        MessagingSDK.getInstance().initializeForVelocity(messagesDir);
        logger.info("MessagingSDK inicializado para Velocity");
    }

    @Override
    public void shutdown() {
        logger.info("Finalizando Controller Core (Proxy)...");
        
        moduleManager.disableAllModules();
        
        shutdownSharedServices();
        
        logger.info("Controller Core (Proxy) finalizado!");
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        initialize();
    }

    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        shutdown();
    }

    private String getProp(String key, String def) {
        String env = System.getenv(key);
        return env != null ? env : System.getProperty(key, def);
    }
}
