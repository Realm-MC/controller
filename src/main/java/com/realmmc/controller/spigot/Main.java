package com.realmmc.controller.spigot;

import com.realmmc.controller.core.ControllerCore;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.retrooper.packetevents.PacketEvents;
import com.realmmc.controller.spigot.display.DisplayItemService;
import com.realmmc.controller.spigot.display.config.DisplayConfigLoader;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    
    private DisplayItemService displayItemService;
    private DisplayConfigLoader displayConfigLoader;
    
    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;
    private java.util.logging.Logger logger;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();

        PacketEvents.getAPI().init();
        
        initialize();
        
        getLogger().info("Controller (Spigot) habilitado! " + getDescription().getVersion());
    }
    
    @Override
    public void onDisable() {
        shutdown();
        
        try { 
            PacketEvents.getAPI().terminate(); 
        } catch (Exception ignored) {}
        
        getLogger().info("Controller (Spigot) desabilitado! " + getDescription().getVersion());
        instance = null;
    }

    public void initialize() {
        logger.info("Inicializando Controller Core (Spigot)...");
        
        initializeSharedServices();
        
        serviceRegistry = new ServiceRegistry(logger);
        moduleManager = new ModuleManager(logger);

        moduleManager.registerModule(new DatabaseModule(logger));
        moduleManager.registerModule(new SchedulerModule(this, this, logger));
        moduleManager.registerModule(new ProfileModule(logger));
        moduleManager.registerModule(new CommandModule(logger));
        moduleManager.registerModule(new SpigotModule(this, logger));

        moduleManager.enableAllModules();
        
        displayItemService = new DisplayItemService();
        
        saveDefaultConfigFiles();
        
        displayConfigLoader = new DisplayConfigLoader(this);
        displayConfigLoader.load();
        
        logger.info("Controller Core (Spigot) inicializado com sucesso!");
    }
    
    public void shutdown() {
        logger.info("Finalizando Controller Core (Spigot)...");
        
        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }
        
        shutdownSharedServices();
        
        logger.info("Controller Core (Spigot) finalizado!");
    }
    
    protected void initializeSharedServices() {
        logger.info("Inicializando serviços compartilhados do Controller Core...");
    }
    
    protected void shutdownSharedServices() {
        logger.info("Finalizando serviços compartilhados do Controller Core...");
    }
    
    private void saveDefaultConfigFiles() {
        if (getResource("displays.yml") != null) {
            saveResource("displays.yml", false);
        }
    }
    
    public DisplayItemService getDisplayItemService() {
        return displayItemService;
    }
    
    public DisplayConfigLoader getDisplayConfigLoader() {
        return displayConfigLoader;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
}
