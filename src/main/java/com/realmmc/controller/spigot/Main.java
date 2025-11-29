package com.realmmc.controller.spigot;

import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.CoreModule;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.modules.SystemType;
import com.realmmc.controller.core.services.ServiceRegistry;

import com.realmmc.controller.modules.chat.SpigotChatModule;
import com.realmmc.controller.modules.nametag.NametagModule;
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;
import com.realmmc.controller.modules.tablist.SpigotTablistModule;

import com.realmmc.controller.shared.geoip.GeoIPService;
import com.realmmc.controller.shared.messaging.MessagingSDK;

import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;

import com.github.retrooper.packetevents.PacketEvents;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        logger.info("Controller Core (Spigot - v2.1 Hybrid) carregando...");
    }

    @Override
    public void onEnable() {
        try {
            if (PacketEvents.getAPI() == null) {
                logger.severe("PacketEvents não encontrado! O plugin PacketEvents é obrigatório.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            logger.info("Inicializando Controller Core (Spigot - v2.1)...");

            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            File messagesDir = new File(getDataFolder(), "messages");
            if (!messagesDir.exists()) {
                messagesDir.mkdirs();
            }
            copyResourceIfNotExists("messages/pt_BR.properties", messagesDir.toPath().resolve("pt_BR.properties"));
            copyResourceIfNotExists("messages/en.properties", messagesDir.toPath().resolve("en.properties"));

            saveDefaultConfigResource("displays.yml");
            saveDefaultConfigResource("holograms.yml");
            saveDefaultConfigResource("npcs.yml");
            saveDefaultConfigResource("particles.yml");

            serviceRegistry = new ServiceRegistry(logger);

            serviceRegistry.registerService(Plugin.class, this);
            logger.info("Instância do Plugin (Main) registrada no ServiceRegistry.");

            geoIPService = new GeoIPService(getDataFolder(), logger);
            serviceRegistry.registerService(GeoIPService.class, geoIPService);

            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForSpigot(messagesDir);
                logger.info("MessagingSDK inicializado para Spigot.");
            }

            displayItemService = new DisplayItemService();
            serviceRegistry.registerService(DisplayItemService.class, displayItemService);

            hologramService = new HologramService();
            serviceRegistry.registerService(HologramService.class, hologramService);

            npcService = new NPCService();
            serviceRegistry.registerService(NPCService.class, npcService);
            getServer().getPluginManager().registerEvents(npcService, this);

            logger.info("Serviços de Entidades Base inicializados.");

            moduleManager = new ModuleManager(logger);
            serviceRegistry.registerService(ModuleManager.class, moduleManager);

            moduleManager.autoRegisterModules(AutoRegister.Platform.SPIGOT, getClass());

            moduleManager.registerModule(new SchedulerModule(null, this, logger));
            moduleManager.registerModule(new SpigotModule(this, logger));

            moduleManager.enableAllModules();

            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    npcService.resendAllTo(p);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao atualizar NPCs para " + p.getName(), e);
                }
            }

            getServer().getScheduler().runTask(this, this::activateOptionalModules);

            logger.info("Controller Core inicializado. Aguardando reivindicação de subsistemas...");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro fatal durante a inicialização do Controller!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void activateOptionalModules() {
        logger.info("Verificando módulos opcionais...");

        if (!moduleManager.isClaimed(SystemType.CHAT)) {
            CoreModule chatModule = new SpigotChatModule(logger);
            moduleManager.registerModule(chatModule);
            moduleManager.enableModule(chatModule);
            logger.info("-> SpigotChatModule ativado (Padrão).");
        } else {
            logger.info("-> SpigotChatModule IGNORADO (Sistema CHAT reivindicado externamente).");
        }

        if (!moduleManager.isClaimed(SystemType.TAGS)) {
            CoreModule nametagModule = new NametagModule(logger);
            moduleManager.registerModule(nametagModule);
            moduleManager.enableModule(nametagModule);
            logger.info("-> NametagModule ativado (Padrão).");
        } else {
            logger.info("-> NametagModule IGNORADO (Sistema TAGS reivindicado externamente).");
        }

        if (!moduleManager.isClaimed(SystemType.TABLIST)) {
            CoreModule tablistModule = new SpigotTablistModule(logger);
            moduleManager.registerModule(tablistModule);
            moduleManager.enableModule(tablistModule);
            logger.info("-> SpigotTablistModule ativado (Padrão).");
        } else {
            logger.info("-> SpigotTablistModule IGNORADO (Sistema TABLIST reivindicado externamente).");
        }

    }

    @Override
    public void onDisable() {
        try {
            logger.info("Finalizando Controller Core (Spigot - v2)...");

            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

            if (displayItemService != null) {
                try { displayItemService.cleanup(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao limpar DisplayItems.", e); }
            }
            if (hologramService != null) {
                try { hologramService.clearAll(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao limpar Hologramas.", e); }
            }
            if (npcService != null) {
                try { npcService.cleanup(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao limpar NPCs.", e); }
            }

        } finally {
            if (geoIPService != null) {
                try { geoIPService.close(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao fechar GeoIPService.", e); }
            }
            MessagingSDK.getInstance().shutdown();

            ServiceRegistry currentRegistry = ServiceRegistry.getInstance();
            if (currentRegistry != null) {
                try { currentRegistry.unregisterService(NPCService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(HologramService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(DisplayItemService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(GeoIPService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(ModuleManager.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(Plugin.class); } catch (Exception e) {}
            }

            serviceRegistry = null;
            moduleManager = null;
            displayItemService = null;
            hologramService = null;
            npcService = null;
            geoIPService = null;

            instance = null;

            logger.info("Controller Core (Spigot - v2) finalizado.");
        }
    }

    private void saveDefaultConfigResource(String resourceName) {
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) {
            try {
                saveResource(resourceName, false);
            } catch (IllegalArgumentException e) {
                logger.warning("Recurso '" + resourceName + "' não encontrado no JAR.");
            }
        }
    }

    private void copyResourceIfNotExists(String resourcePath, Path targetPath) throws IOException {
        if (Files.notExists(targetPath)) {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            try (InputStream stream = getClass().getResourceAsStream("/" + resourcePath)) {
                if (stream == null) return;
                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}