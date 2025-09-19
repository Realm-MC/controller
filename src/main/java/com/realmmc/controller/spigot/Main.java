package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.spigot.display.DisplayItemService;
import com.realmmc.controller.spigot.display.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.display.config.DisplayEntry;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;
    
    @Getter
    private DisplayItemService displayItemService;
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
            if (getResource("displays.yml") != null) {
                saveResource("displays.yml", false);
            }
            displayConfigLoader = new DisplayConfigLoader();
            displayConfigLoader.load();

            List<DisplayEntry> entries = displayConfigLoader.getEntries();
            int loadedCount = 0;
            for (DisplayEntry entry : entries) {
                if (entry.getType() == DisplayEntry.Type.DISPLAY_ITEM) {
                    try {
                        World world = getServer().getWorld(entry.getWorld());
                        if (world != null && entry.getX() != null && entry.getY() != null && entry.getZ() != null) {
                            Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(), 
                                    entry.getYaw() != null ? entry.getYaw() : 0, 
                                    entry.getPitch() != null ? entry.getPitch() : 0);
                            
                            ItemStack item = new ItemStack(Material.valueOf(entry.getItem()));
                            
                            List<String> lines = new ArrayList<>();
                            lines.add("<yellow><b>Display #" + entry.getId() + "</b>");
                            lines.add("<gray>Carregado automaticamente");
                            lines.add("<white>Item: <aqua>" + entry.getItem());
                            
                            displayItemService.show(null, location, item, lines, true);
                            loadedCount++;
                        } else {
                            logger.warning("Display ID " + entry.getId() + " tem dados inválidos, ignorando.");
                        }
                    } catch (Exception e) {
                        logger.warning("Erro ao carregar display ID " + entry.getId() + ": " + e.getMessage());
                    }
                }
            }
            
            if (loadedCount > 0) {
                logger.info("Carregados " + loadedCount + " displays salvos.");
            }
            
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
}
