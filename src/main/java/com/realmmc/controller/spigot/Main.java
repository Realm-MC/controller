package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    
    @Getter
    private DisplayItemService displayItemService;
    @Getter
    private HologramService hologramService;
    @Getter
    private NPCService npcService;
    @Getter
    private DisplayConfigLoader displayConfigLoader;
    
    @Getter
    private ModuleManager moduleManager;
    @Getter
    private ServiceRegistry serviceRegistry;
    private Logger logger;

    @Override
    public void onLoad() {
        instance = this;
        logger = getLogger();
        logger.info("Controller Core (Spigot) carregado.");
    }

    @Override
    public void onEnable() {
        try {
            logger.info("Inicializando Controller Core (Spigot)...");
            logger.info("Inicializando serviços compartilhados do Controller Core...");
            serviceRegistry = new ServiceRegistry(logger);
            moduleManager = new ModuleManager(logger);
            moduleManager.registerModule(new DatabaseModule(logger));
            moduleManager.registerModule(new SchedulerModule(this, this, logger));
            moduleManager.registerModule(new ProfileModule(logger));
            moduleManager.registerModule(new CommandModule(logger));
            moduleManager.registerModule(new SpigotModule(this, logger));
            moduleManager.enableAllModules();

            displayItemService = new DisplayItemService();
            hologramService = new HologramService();
            npcService = new NPCService();
            getServer().getPluginManager().registerEvents(npcService, this);
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { npcService.resendAllTo(p); } catch (Exception ignored) {}
            }
            
            if (getResource("displays.yml") != null) {
                saveResource("displays.yml", false);
            }
            if (getResource("holograms.yml") != null) {
                saveResource("holograms.yml", false);
            }
            if (getResource("npcs.yml") != null) {
                saveResource("npcs.yml", false);
            }
            
            displayConfigLoader = new DisplayConfigLoader();
            displayConfigLoader.load();
            
            logger.info("Controller Core (Spigot) inicializado com sucesso!");
        } catch (Exception e) {
            logger.severe("Erro durante inicialização: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            logger.info("Finalizando Controller Core (Spigot)...");

            if (displayItemService != null) {
                displayItemService.clearAll();
                logger.info("Todas as entities de display foram removidas.");
            }

            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }
            
            logger.info("Finalizando serviços compartilhados do Controller Core...");
            
            logger.info("Controller Core (Spigot) finalizado com sucesso!");
        } catch (Exception e) {
            logger.severe("Erro durante finalização: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public NPCService getNPCService() {
        return npcService;
    }
}
