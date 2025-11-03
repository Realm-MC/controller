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
import com.realmmc.controller.spigot.entities.npcs.NPCService;

// Lombok import
import lombok.Getter;

// Bukkit imports
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Import Plugin interface
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
    private static Main instance; // Static instance for easy access

    // Spigot Entity Services
    @Getter
    private DisplayItemService displayItemService;
    @Getter
    private HologramService hologramService;
    @Getter
    private NPCService npcService;

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
            // saveDefaultConfig();

            // --- Inicialização de Serviços Core ---
            serviceRegistry = new ServiceRegistry(logger);

            // <<< REGISTRO DA INSTÂNCIA DO PLUGIN >>>
            serviceRegistry.registerService(Plugin.class, this); // Registra a própria instância do plugin
            logger.info("Instância do Plugin (Main) registrada no ServiceRegistry.");

            // GeoIP
            geoIPService = new GeoIPService(getDataFolder(), logger);
            serviceRegistry.registerService(GeoIPService.class, geoIPService);
            logger.info("GeoIPService registrado.");

            // Messaging
            if (!MessagingSDK.getInstance().isInitialized()) {
                MessagingSDK.getInstance().initializeForSpigot(messagesDir);
                logger.info("MessagingSDK inicializado para Spigot.");
            } else {
                logger.warning("Tentativa de reinicializar MessagingSDK ignorada.");
            }

            // --- Inicialização de Serviços de Entidades (Spigot) ---
            displayItemService = new DisplayItemService();
            hologramService = new HologramService();
            npcService = new NPCService(); // O construtor NÃO chama mais o loadSavedNPCs()

            // <<< REGISTRO DOS SERVIÇOS DE ENTIDADES >>>
            serviceRegistry.registerService(DisplayItemService.class, displayItemService);
            serviceRegistry.registerService(HologramService.class, hologramService);
            serviceRegistry.registerService(NPCService.class, npcService);
            logger.info("Serviços de Entidades (Display, Hologram, NPC) inicializados e registrados no ServiceRegistry.");

            // --- Inicialização do ModuleManager e Módulos ---
            moduleManager = new ModuleManager(logger);

            // Auto-registro de módulos gerais e SPIGOT
            moduleManager.autoRegisterModules(AutoRegister.Platform.SPIGOT, getClass());

            // Registro manual de módulos específicos da plataforma
            moduleManager.registerModule(new SchedulerModule(null, this, logger)); // Passa 'this' (Plugin)
            moduleManager.registerModule(new SpigotModule(this, logger));

            // Habilita todos os módulos registrados
            moduleManager.enableAllModules();

            // --- Registros Finais ---
            getServer().getPluginManager().registerEvents(npcService, this);
            logger.info("NPCService registrado como Listener do Bukkit.");

            // A chamada npcService.loadSavedNPCs() foi movida para SpigotModule.onEnable()

            // Reenvia NPCs para jogadores já online (em caso de /reload)
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    npcService.resendAllTo(p);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao reenviar NPCs para " + p.getName() + " no onEnable.", e);
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

            // --- Desregistra Serviços do Registry ---
            ServiceRegistry currentRegistry = ServiceRegistry.getInstance(); // Pega instância atual
            if (currentRegistry != null) {
                try { currentRegistry.unregisterService(NPCService.class); } catch (Exception e) { /* Log */ }
                try { currentRegistry.unregisterService(HologramService.class); } catch (Exception e) { /* Log */ }
                try { currentRegistry.unregisterService(DisplayItemService.class); } catch (Exception e) { /* Log */ }
                try { currentRegistry.unregisterService(GeoIPService.class); } catch (Exception e) { /* Log */ }
                try { currentRegistry.unregisterService(Plugin.class); } catch (Exception e) { /* Log */ }
                logger.info("Serviços desregistrados do ServiceRegistry.");
            }

            // --- Limpa Referências ---
            serviceRegistry = null;
            moduleManager = null;
            displayItemService = null;
            hologramService = null;
            npcService = null;
            geoIPService = null;

            instance = null; // Limpa instância estática

            logger.info("Controller Core (Spigot - v2) finalizado.");
        }
    }

    /**
     * Salva um recurso da pasta 'resources' para a pasta de dados do plugin,
     * apenas se ele não existir.
     */
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

    /**
     * Copia um recurso interno para um local específico se não existir.
     */
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