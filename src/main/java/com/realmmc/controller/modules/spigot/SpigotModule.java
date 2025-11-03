package com.realmmc.controller.modules.spigot;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.services.SessionService; // Assumindo que SessionService está neste pacote
import com.realmmc.controller.shared.profile.Profile; // Importar Profile
import com.realmmc.controller.shared.profile.ProfileService; // Importar ProfileService
import com.realmmc.controller.shared.role.PermissionRefresher;
import com.realmmc.controller.shared.role.RoleKickHandler; // <<< IMPORTAR
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.commands.CommandManager;
import com.realmmc.controller.spigot.listeners.ListenersManager;
import com.realmmc.controller.spigot.permission.SpigotPermissionInjector;
import com.realmmc.controller.spigot.permission.SpigotPermissionRefresher;
import com.realmmc.controller.spigot.sounds.SpigotSoundPlayer;
// --- Imports para Heartbeat ---
import com.realmmc.controller.shared.session.SessionTrackerService; // Importar
import com.realmmc.controller.shared.utils.TaskScheduler; // Importar
import org.bukkit.Bukkit; // Importar Bukkit
import org.bukkit.entity.Player; // Importar Player
import java.util.Optional; // Importar Optional
import java.util.concurrent.ScheduledFuture; // Importar ScheduledFuture
import java.util.concurrent.TimeUnit; // Importar TimeUnit
// --- Fim Imports Heartbeat ---
import org.bukkit.plugin.Plugin;
import org.bukkit.event.HandlerList;

// --- Imports para ViaVersion (Heartbeat) ---
import com.viaversion.viaversion.api.Via;
// --- Fim Imports ViaVersion ---

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotModule extends AbstractCoreModule {
    private final Plugin plugin;
    private SessionService sessionServiceInstance;
    private SpigotPermissionInjector permissionInjectorInstance;
    // --- Campos para Heartbeat ---
    private Optional<SessionTrackerService> sessionTrackerServiceOpt; // Para acesso ao serviço
    private ScheduledFuture<?> heartbeatTaskFuture = null; // Para guardar a referência da tarefa
    // Flag ViaVersion (inicializada no construtor ou onEnable)
    private final boolean viaVersionApiAvailable;
    // --- Fim Campos Heartbeat ---

    public SpigotModule(Plugin plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
        // Inicializa a flag ViaVersion aqui
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
    }

    @Override public String getName() { return "SpigotModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo específico para funcionalidades Spigot (v2)."; }

    @Override public int getPriority() { return 50; } // Carrega por último

    @Override public String[] getDependencies() {
        // Adicionar ProfileModule como dependência por causa do SessionTrackerService
        return new String[]{
                "Profile", "RoleModule", "SchedulerModule", "Command", "Preferences", "Particle"
        };
    }

    @Override
    protected void onEnable() {
        logger.info("Registrando serviços e listeners específicos do Spigot (v2)...");
        // --- Obter SessionTrackerService ---
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        if(sessionTrackerServiceOpt.isEmpty()){
            logger.warning("SessionTrackerService não encontrado no SpigotModule! Heartbeat não funcionará.");
        }
        // --- Fim Obtenção ---

        // 1. Registar SoundPlayer
        try {
            ServiceRegistry.getInstance().registerService(SoundPlayer.class, new SpigotSoundPlayer());
            logger.info("SpigotSoundPlayer registrado.");
        } catch (Exception e) { logger.log(Level.WARNING, "Falha ao registrar SpigotSoundPlayer", e); }

        // 2. Registar Comandos e Listeners
        try { CommandManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar comandos Spigot!", e); }
        try { ListenersManager.registerAll(plugin); }
        catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar listeners Spigot!", e); }


        // --- INTEGRAÇÃO PERMISSÕES E SESSÃO (v2) ---

        // 3. SessionService
        try {
            this.sessionServiceInstance = new SessionService(logger); // Passa o logger do módulo
            Bukkit.getServer().getPluginManager().registerEvents(sessionServiceInstance, plugin);
            logger.info("SessionService (Listener) registrado.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao registrar SessionService!", e);
            this.sessionServiceInstance = null;
        }

        // 4. SpigotPermissionInjector
        if (this.sessionServiceInstance != null) {
            try {
                this.permissionInjectorInstance = new SpigotPermissionInjector(plugin, logger); // Passa o logger do módulo
                Bukkit.getServer().getPluginManager().registerEvents(permissionInjectorInstance, plugin);
                logger.info("SpigotPermissionInjector (Listener) registrado.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao registrar SpigotPermissionInjector!", e);
                this.permissionInjectorInstance = null;
            }
        } else {
            logger.severe("SpigotPermissionInjector não registrado porque SessionService falhou ao inicializar.");
        }

        // 5. SpigotPermissionRefresher e RoleKickHandler
        ServiceRegistry.getInstance().getService(RoleService.class).ifPresentOrElse(roleService -> {
            if (this.permissionInjectorInstance != null) {
                try {
                    PermissionRefresher spigotRefresher = new SpigotPermissionRefresher(plugin, logger); // Passa o logger
                    ServiceRegistry.getInstance().registerService(PermissionRefresher.class, spigotRefresher);
                    logger.info("SpigotPermissionRefresher registrado no ServiceRegistry.");
                } catch (Exception e) { logger.log(Level.SEVERE, "Falha ao registrar SpigotPermissionRefresher!", e); }

                // <<< Inicializar o KickHandler AQUI >>>
                try {
                    RoleKickHandler.PlatformKicker kicker = (uuid, formattedKickMessage) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> { // Usa a instância do plugin
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                player.kickPlayer(formattedKickMessage);
                            }
                        });
                    };
                    RoleKickHandler.initialize(kicker);
                    logger.info("RoleKickHandler inicializado para plataforma Spigot.");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Falha ao inicializar RoleKickHandler no SpigotModule!", e);
                }
                // <<< FIM KickHandler >>>

            } else {
                logger.severe("SpigotPermissionRefresher (e KickHandler) não registrado porque o SpigotPermissionInjector falhou.");
            }
        }, () -> {
            logger.severe("RoleService NÃO detectado. Integração de permissões Spigot não será configurada.");
        });
        // --- FIM INTEGRAÇÃO (v2) ---

        // --- Iniciar Tarefa de Heartbeat ---
        startHeartbeatTask();
        // --- Fim Heartbeat ---

        logger.info("SpigotModule habilitado com sucesso.");
    }

    @Override
    protected void onDisable() {
        logger.info("Desabilitando SpigotModule...");

        // --- Parar Tarefa de Heartbeat ---
        stopHeartbeatTask();
        // --- Fim Heartbeat ---

        // Desregistro de Serviços
        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        ServiceRegistry.getInstance().unregisterService(PermissionRefresher.class);
        logger.info("Serviços Spigot desregistrados.");

        // Desregistro de Listeners
        if (sessionServiceInstance != null) {
            try { HandlerList.unregisterAll(sessionServiceInstance); }
            catch (Exception e) { logger.log(Level.WARNING, "Erro ao desregistrar SessionService.", e); }
            sessionServiceInstance = null;
        }
        if (permissionInjectorInstance != null) {
            try {
                HandlerList.unregisterAll(permissionInjectorInstance);
                permissionInjectorInstance.cleanupOnDisable(); // Chama limpeza interna
            }
            catch (Exception e) { logger.log(Level.WARNING, "Erro ao desregistrar SpigotPermissionInjector.", e); }
            permissionInjectorInstance = null;
        }

        try { HandlerList.unregisterAll(plugin); logger.info("Outros listeners Spigot desregistrados."); }
        catch (Exception e) { logger.log(Level.WARNING, "Erro ao desregistrar outros listeners do plugin.", e); }

        // O RoleKickHandler.shutdown() é chamado pelo RoleModule.onDisable()

        logger.info("SpigotModule desabilitado.");
    }

    // --- Métodos para Heartbeat Task ---
    private void startHeartbeatTask() {
        if (heartbeatTaskFuture != null && !heartbeatTaskFuture.isDone()) {
            logger.fine("Tarefa de Heartbeat já está rodando.");
            return;
        }

        sessionTrackerServiceOpt.ifPresentOrElse(sessionTracker -> {
            try {
                // Roda a cada 15 segundos, começando após 10 segundos
                heartbeatTaskFuture = TaskScheduler.runAsyncTimer(() -> {
                    try {
                        runHeartbeat(sessionTracker);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Erro na execução periódica do Heartbeat Task (Spigot)", e);
                    }
                }, 10, 15, TimeUnit.SECONDS);
                logger.info("Tarefa de Heartbeat (Spigot) iniciada.");
            } catch (IllegalStateException e) {
                logger.log(Level.SEVERE, "Falha ao agendar Heartbeat Task (Spigot): TaskScheduler não inicializado?", e);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro inesperado ao agendar Heartbeat Task (Spigot)", e);
            }
        }, () -> {
            logger.warning("Heartbeat Task (Spigot) não iniciada: SessionTrackerService não encontrado.");
        });
    }

    private void stopHeartbeatTask() {
        if (heartbeatTaskFuture != null) {
            try {
                heartbeatTaskFuture.cancel(false); // false: não interrompe se estiver rodando
                logger.info("Tarefa de Heartbeat (Spigot) parada.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar Heartbeat Task (Spigot)", e);
            } finally {
                heartbeatTaskFuture = null;
            }
        }
    }

    private void runHeartbeat(SessionTrackerService sessionTracker) {
        // Itera sobre os jogadores online NESTE servidor Spigot
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue; // Verificação extra

            UUID uuid = player.getUniqueId();
            String serverName = Bukkit.getServer().getName(); // Nome deste servidor Spigot
            int ping = player.getPing();
            int protocol = -1; // Tentar obter o protocolo novamente
            try {
                if(viaVersionApiAvailable) { // Usar a flag já existente
                    protocol = Via.getAPI().getPlayerVersion(uuid);
                } else {
                    // Adicionar fallbacks se necessário (ProtocolSupport, NMS) como no SpigotPlayerListener
                    try {
                        Class<?> protocolSupportApi = Class.forName("protocolsupport.api.ProtocolSupportAPI");
                        Object apiInstance = protocolSupportApi.getMethod("getAPI").invoke(null);
                        protocol = (int) apiInstance.getClass().getMethod("getProtocolVersion", Player.class).invoke(apiInstance, player);
                    } catch (Exception | NoClassDefFoundError ignored) {
                        try {
                            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                            Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
                            Object networkManager = connection.getClass().getField("networkManager").get(connection);
                            try {
                                protocol = (int) networkManager.getClass().getMethod("getVersion").invoke(networkManager);
                            } catch (NoSuchMethodException ex) {
                                protocol = (int) networkManager.getClass().getMethod("getProtocolVersion").invoke(networkManager);
                            }
                        } catch (Exception | NoClassDefFoundError nmsEx) { protocol = -1;}
                    }
                }
            } catch (Exception | NoClassDefFoundError ignored) { protocol = -1; }


            // Chama o método do serviço para atualizar o Redis
            try {
                sessionTracker.updateHeartbeat(uuid, serverName, ping, protocol);
            } catch (Exception e) {
                // Logar erro específico da atualização, mas continuar para outros jogadores
                logger.log(Level.WARNING, "Erro ao enviar heartbeat para " + player.getName() + " (UUID: " + uuid + ")", e);
            }
        }
        // Log opcional para indicar que o heartbeat rodou
         logger.finest("Heartbeat Task (Spigot) executado.");
    }
    // --- Fim Métodos Heartbeat ---
}