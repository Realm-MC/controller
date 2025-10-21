package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.scheduler.SchedulerModule; // Import necessário
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.modules.spigot.sounds.SoundModule;
import com.realmmc.controller.shared.geoip.GeoIPService;
import com.realmmc.controller.shared.messaging.MessagingSDK;
import com.realmmc.controller.shared.permission.PermissionService; // Necessário para PlayerDisplayService
import com.realmmc.controller.shared.profile.PlayerDisplayService; // Necessário para registrar
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

public class Main extends JavaPlugin {
    @Getter
    private static Main instance;

    @Getter
    private DisplayItemService displayItemService;
    @Getter
    private HologramService hologramService;
    @Getter
    private NPCService npcService;

    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;
    private GeoIPService geoIPService;

    private Logger logger;

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

            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            File messagesDir = new File(getDataFolder(), "messages");
            if (!messagesDir.exists()) {
                messagesDir.mkdirs();
            }

            saveResource("messages/pt_BR.properties", false);
            saveResource("messages/en.properties", false);
            saveDefaultConfigResource("displays.yml");
            saveDefaultConfigResource("holograms.yml");
            saveDefaultConfigResource("npcs.yml");
            saveDefaultConfigResource("particles.yml");

            serviceRegistry = new ServiceRegistry(logger);

            geoIPService = new GeoIPService(getDataFolder(), logger);
            serviceRegistry.registerService(GeoIPService.class, geoIPService);
            logger.info("Serviço registrado: GeoIPService");

            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForSpigot(messagesDir);
                logger.info("MessagingSDK inicializado para Spigot");
            } else {
                logger.warning("Tentativa de reinicializar MessagingSDK ignorada.");
            }

            moduleManager = new ModuleManager(logger);

            displayItemService = new DisplayItemService();
            hologramService = new HologramService();
            npcService = new NPCService();

            moduleManager.autoRegisterModules(AutoRegister.Platform.SPIGOT, getClass());

            moduleManager.registerModule(new SchedulerModule(null, this, logger));
            moduleManager.registerModule(new SpigotModule(this, logger));
            moduleManager.registerModule(new SoundModule(logger));

            moduleManager.enableAllModules();

            PermissionService permissionService = serviceRegistry.getService(PermissionService.class)
                    .orElseThrow(() -> new IllegalStateException("PermissionService não encontrado! PlayerDisplayService não pode ser registrado."));
            PlayerDisplayService playerDisplayService = new PlayerDisplayService(permissionService);
            serviceRegistry.registerService(PlayerDisplayService.class, playerDisplayService);
            logger.info("Serviço registrado: PlayerDisplayService");

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

            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

            if (displayItemService != null) {
                displayItemService.clearAll();
            }
            if (hologramService != null) {
                hologramService.clearAll();
            }
            if (npcService != null) {
                npcService.despawnAll();
            }

        } finally {
            if (geoIPService != null) {
                geoIPService.close();
            }
            if (serviceRegistry != null) {
                serviceRegistry.unregisterService(GeoIPService.class);
                serviceRegistry.unregisterService(PlayerDisplayService.class);
            }
            MessagingSDK.getInstance().shutdown();

            logger.info("Controller Core (Spigot) finalizado.");
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