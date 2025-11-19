package com.realmmc.controller.spigot;

// Core Controller imports
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.services.ServiceRegistry;

// Modules imports
import com.realmmc.controller.modules.scheduler.SchedulerModule;
import com.realmmc.controller.modules.spigot.SpigotModule;

// Shared services imports
import com.realmmc.controller.shared.geoip.GeoIPService;
import com.realmmc.controller.shared.messaging.MessagingSDK;

// Spigot specific entity services imports
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.nametag.NametagService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;

// PacketEvents
import com.github.retrooper.packetevents.PacketEvents;

// Lombok import
import lombok.Getter;

// Bukkit imports
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

// Java IO and NIO imports
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Logging imports
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    @Getter
    private static Main instance;

    // Spigot Entity Services
    @Getter
    private DisplayItemService displayItemService;
    @Getter
    private HologramService hologramService;
    @Getter
    private NPCService npcService;
    @Getter
    private NametagService nametagService;

    // Core Controller Components
    private ModuleManager moduleManager;
    private ServiceRegistry serviceRegistry;

    // Shared Services
    private GeoIPService geoIPService;

    // Logger instance
    private Logger logger;

    @Override
    public void onLoad() {
        instance = this;
        this.logger = getLogger();
        logger.info("Controller Core (Spigot - v2) carregando...");
    }

    @Override
    public void onEnable() {
        try {
            if (PacketEvents.getAPI() == null) {
                logger.severe("PacketEvents não encontrado! O plugin PacketEvents é obrigatório.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            logger.info("Inicializando Controller Core (Spigot - v2)...");

            // --- Criação de Pastas e Cópia de Arquivos Padrão ---
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

            // --- Inicialização de Serviços Core ---
            serviceRegistry = new ServiceRegistry(logger);

            // <<< REGISTRO DA INSTÂNCIA DO PLUGIN >>>
            serviceRegistry.registerService(Plugin.class, this);
            logger.info("Instância do Plugin (Main) registrada no ServiceRegistry.");

            // GeoIP
            geoIPService = new GeoIPService(getDataFolder(), logger);
            serviceRegistry.registerService(GeoIPService.class, geoIPService);
            logger.info("GeoIPService registrado.");

            // Messaging (Chat, Title, ActionBar)
            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForSpigot(messagesDir);
                logger.info("MessagingSDK inicializado para Spigot.");
            } else {
                logger.warning("Tentativa de reinicializar MessagingSDK ignorada.");
            }

            // --- Serviços Independentes (DEVEM vir ANTES dos módulos) ---

            // 1. Display Items
            displayItemService = new DisplayItemService();
            serviceRegistry.registerService(DisplayItemService.class, displayItemService);

            // 2. Hologramas
            hologramService = new HologramService();
            serviceRegistry.registerService(HologramService.class, hologramService);

            // 3. NPCs (Movido para cá, pois SpigotModule precisa dele para o comando /npc)
            npcService = new NPCService();
            serviceRegistry.registerService(NPCService.class, npcService);
            getServer().getPluginManager().registerEvents(npcService, this);

            logger.info("Serviços de Entidades Base (Display, Hologram, NPC) inicializados.");

            // --- Inicialização do ModuleManager e Módulos ---
            moduleManager = new ModuleManager(logger);

            // Auto-registro de módulos gerais e SPIGOT
            moduleManager.autoRegisterModules(AutoRegister.Platform.SPIGOT, getClass());

            // Registro manual de módulos específicos
            moduleManager.registerModule(new SchedulerModule(null, this, logger));
            moduleManager.registerModule(new SpigotModule(this, logger));

            // Habilita todos os módulos (SpigotModule -> CommandManager -> NpcCommand -> Pega NPCService)
            moduleManager.enableAllModules();

            // --- Serviços Dependentes de Módulos (DEVEM vir DEPOIS dos módulos) ---

            // 4. Nametags (Depende de RoleService, que é criado dentro dos módulos)
            nametagService = new NametagService();
            serviceRegistry.registerService(NametagService.class, nametagService);
            getServer().getPluginManager().registerEvents(nametagService, this);

            logger.info("Serviços dependentes (Nametag) inicializados após módulos.");

            // --- Pós-Inicialização ---

            // Reenvia NPCs e Tags para jogadores já online (em caso de /reload)
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    npcService.resendAllTo(p);
                    nametagService.updateTag(p);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao atualizar entidades para " + p.getName() + " no onEnable.", e);
                }
            }

            logger.info("Controller Core (Spigot - v2) inicializado com sucesso!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro fatal durante a inicialização do Controller!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            logger.info("Finalizando Controller Core (Spigot - v2)...");

            // --- Desabilita Módulos na Ordem Inversa ---
            if (moduleManager != null) {
                moduleManager.disableAllModules();
            }

            // --- Limpeza de Entidades ---
            if (displayItemService != null) {
                try { displayItemService.clearAll(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao limpar DisplayItems.", e); }
            }
            if (hologramService != null) {
                try { hologramService.clearAll(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao limpar Hologramas.", e); }
            }
            if (npcService != null) {
                try { npcService.despawnAll(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao despawn NPCs.", e); }
            }

        } finally {
            // --- Finaliza Serviços Core ---
            if (geoIPService != null) {
                try { geoIPService.close(); } catch (Exception e) { logger.log(Level.WARNING, "Erro ao fechar GeoIPService.", e); }
            }
            MessagingSDK.getInstance().shutdown();

            // Não terminamos PacketEvents aqui pois é plugin externo

            // --- Desregistra Serviços do Registry ---
            ServiceRegistry currentRegistry = ServiceRegistry.getInstance();
            if (currentRegistry != null) {
                try { currentRegistry.unregisterService(NametagService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(NPCService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(HologramService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(DisplayItemService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(GeoIPService.class); } catch (Exception e) {}
                try { currentRegistry.unregisterService(Plugin.class); } catch (Exception e) {}
                logger.info("Serviços desregistrados do ServiceRegistry.");
            }

            // --- Limpa Referências ---
            serviceRegistry = null;
            moduleManager = null;
            displayItemService = null;
            hologramService = null;
            npcService = null;
            nametagService = null;
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
                logger.info("Arquivo padrão '" + resourceName + "' copiado para a pasta de dados.");
            } catch (IllegalArgumentException e) {
                logger.warning("Recurso '" + resourceName + "' não encontrado no JAR para salvar como padrão.");
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
                if (stream == null) {
                    logger.warning("Recurso não encontrado no JAR: /" + resourcePath);
                    return;
                }
                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.finer("Recurso padrão copiado: /" + resourcePath + " -> " + targetPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Falha ao copiar recurso padrão: /" + resourcePath, e);
                throw e;
            } catch (NullPointerException e) {
                logger.warning("NPE ao tentar obter recurso: /" + resourcePath + ". Verifique o caminho.");
            }
        }
    }
}