package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.commands.CommandModule;
import com.realmmc.controller.modules.database.DatabaseModule;
import com.realmmc.controller.modules.particle.ParticleModule;
import com.realmmc.controller.modules.profile.ProfileModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.modules.spigot.sounds.SoundModule;
import com.realmmc.controller.modules.stats.StatisticsModule;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.entities.particles.ParticleService;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;

    private DisplayItemService displayItemService;
    private HologramService hologramService;
    private NPCService npcService;
    // O ParticleService agora é gerido pelo ParticleModule

    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;

    private Logger logger;

    public DisplayItemService getDisplayItemService() {
        return displayItemService;
    }

    public HologramService getHologramService() {
        return hologramService;
    }

    public NPCService getNpcService() {
        return npcService;
    }

    @Override
    public void onLoad() {
        instance = this;
        this.logger = getLogger();
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

            saveDefaultConfigResource("displays.yml");
            saveDefaultConfigResource("holograms.yml");
            saveDefaultConfigResource("npcs.yml");
            saveDefaultConfigResource("particles.yml");

            displayItemService = new DisplayItemService();
            hologramService = new HologramService();
            npcService = new NPCService();

            moduleManager.registerModule(new SchedulerModule(this, this, logger));
            moduleManager.registerModule(new DatabaseModule(logger));
            moduleManager.registerModule(new ProfileModule(logger));
            moduleManager.registerModule(new StatisticsModule(logger));
            moduleManager.registerModule(new CommandModule(logger));
            moduleManager.registerModule(new SpigotModule(this, logger));
            moduleManager.registerModule(new SoundModule(this, logger));
            moduleManager.registerModule(new ParticleModule(logger)); // Módulo de partículas registado

            moduleManager.enableAllModules();

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
            }
            if (hologramService != null) {
                hologramService.clearAll();
            }
            if (npcService != null) {
                npcService.despawnAll();
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