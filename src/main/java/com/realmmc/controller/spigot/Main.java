package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.modules.spigot.sounds.SoundModule;
import com.realmmc.controller.modules.stats.StatisticsModule;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class Main extends JavaPlugin {
    @Getter
    private static Main instance;

    private DisplayItemService displayItemService;
    private HologramService hologramService;
    private NPCService npcService;

    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;
    private final Logger logger = getLogger();

    @Override
    public void onLoad() {
        instance = this;
        logger.info("Controller Core (Spigot) carregado.");
    }

    @Override
    public void onEnable() {
        try {
            logger.info("Inicializando Controller Core (Spigot)...");

            File messagesDir = new File(getDataFolder(), "messages");
            if (!messagesDir.exists()) {
                messagesDir.mkdirs();
            }
            MessagingSDK.getInstance().initializeForSpigot(messagesDir);
            logger.info("MessagingSDK inicializado para Spigot");

            serviceRegistry = new ServiceRegistry(logger);
            moduleManager = new ModuleManager(logger);

            moduleManager.registerModule(new DatabaseModule(logger));
            moduleManager.registerModule(new SchedulerModule(this, this, logger));
            moduleManager.registerModule(new ProfileModule(logger));
            moduleManager.registerModule(new StatisticsModule(logger));
            moduleManager.registerModule(new CommandModule(logger));
            moduleManager.registerModule(new SpigotModule(this, logger));
            moduleManager.registerModule(new SoundModule(this, logger));

            moduleManager.enableAllModules();

            saveDefaultConfigResource("displays.yml");
            saveDefaultConfigResource("holograms.yml");
            saveDefaultConfigResource("npcs.yml");

            displayItemService = new DisplayItemService();
            hologramService = new HologramService();
            npcService = new NPCService();

            getServer().getPluginManager().registerEvents(npcService, this);
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    npcService.resendAllTo(p);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao reenviar NPCs para o jogador " + p.getName() + " na inicialização.", e);
                }
            }

            logger.info("Controller Core (Spigot) inicializado com sucesso!");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro fatal durante a inicialização do Controller", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            logger.info("Finalizando Controller Core (Spigot)...");

            if (displayItemService != null) {
                displayItemService.clearAll();
                logger.info("Todas as entidades de display de item foram removidas.");
            }
            if (hologramService != null) {
                hologramService.clearAll();
                logger.info("Todos os hologramas foram removidos.");
            }
            if (npcService != null) {
                npcService.despawnAll();
                logger.info("Todos os NPCs foram removidos.");
            }

            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

            logger.info("Controller Core (Spigot) finalizado com sucesso!");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro durante a finalização do Controller", e);
        }
    }

    private void saveDefaultConfigResource(String resourceName) {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) {
            saveResource(resourceName, false);
        }
    }
}