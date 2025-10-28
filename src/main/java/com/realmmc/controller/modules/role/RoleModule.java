package com.realmmc.controller.modules.role;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RedisRoleSyncListener;
import com.realmmc.controller.shared.role.RoleKickHandler;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

// Imports específicos da plataforma
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
// (Não precisamos dos imports com alias)

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.Optional;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class RoleModule extends AbstractCoreModule {

    private RoleService roleService;
    private RedisRoleSyncListener roleSyncListener;
    private RedisMessageListener roleBroadcastListener;

    public RoleModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "RoleModule"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo de gerenciamento de grupos e permissões (v2)."; }

    // Depende de Profile (18), Database (10), Scheduler (15)
    @Override public String[] getDependencies() { return new String[]{"Database", "Profile", "SchedulerModule"}; }

    // <<< CORREÇÃO: Aumentar prioridade para 25 (para carregar depois de Preferences(19) e Statistics(21) se necessário, mas PRINCIPALMENTE depois de Profile(18)) >>>
    @Override public int getPriority() { return 25; }
    // <<< FIM CORREÇÃO >>>

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando RoleService e componentes relacionados (v2)...");

        // 1. Registrar RoleService
        try {
            // Esta chamada (linha 62 nos logs) falhou porque ProfileService (Prioridade 18) falhou (por causa do Mongo)
            this.roleService = new RoleService(logger);
            ServiceRegistry.getInstance().registerService(RoleService.class, roleService);
            logger.info("RoleService registrado com sucesso.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha crítica ao inicializar RoleService!", e);
            throw e; // Impede o módulo de habilitar
        }

        // 2. Sincronizar Grupos Padrão
        try {
            roleService.setupDefaultRoles();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro durante a sincronização inicial dos grupos padrão.", e);
        }

        // 3. Iniciar Listener de Sync (ROLE_SYNC)
        RedisSubscriber redisSubscriber = null;
        try {
            redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            this.roleSyncListener = new RedisRoleSyncListener();
            roleSyncListener.startListening();
            logger.info("RedisRoleSyncListener iniciado e escutando canal ROLE_SYNC.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao obter RedisSubscriber necessário para RoleModule!", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao iniciar RedisRoleSyncListener!", e);
            this.roleSyncListener = null;
        }

        // 4. Inicializar RoleKickHandler (Adiado para o Módulo de Plataforma)
        // Não podemos criar o kicker aqui, pois a plataforma (Plugin/ProxyServer)
        // pode ainda não estar registada.
        // Movido para o ProxyModule/SpigotModule.

        // 5. Registrar Listener de Broadcast (ROLE_BROADCAST)
        if (redisSubscriber != null) {
            try {
                this.roleBroadcastListener = createBroadcastListener();
                if (this.roleBroadcastListener != null) {
                    redisSubscriber.registerListener(RedisChannel.ROLE_BROADCAST, this.roleBroadcastListener);
                    logger.info("RoleBroadcastListener registrado no canal ROLE_BROADCAST.");
                } else {
                    logger.fine("RoleBroadcastListener não criado no onEnable (normal, plataforma ainda não registrada).");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao registrar RoleBroadcastListener!", e);
                this.roleBroadcastListener = null;
            }
        } else {
            logger.severe("RedisSubscriber não encontrado, RoleBroadcastListener não registrado.");
        }

        // <<< CORREÇÃO: O KickHandler deve ser inicializado pelo MÓDULO DA PLATAFORMA >>>
        // <<< Vamos mover essa lógica para ProxyModule/SpigotModule >>>

        // (Removendo a inicialização do KickHandler daqui)

        logger.info("RoleModule (v2) habilitado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("Desabilitando RoleModule (v2)...");

        if (roleSyncListener != null) {
            try { roleSyncListener.stopListening(); }
            catch (Exception e) { logger.log(Level.WARNING, "Erro ao chamar stopListening() no RedisRoleSyncListener.", e); }
        }

        if (roleBroadcastListener != null) {
            try {
                ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                        .ifPresent(sub -> sub.unregisterListener(RedisChannel.ROLE_BROADCAST));
                logger.info("RoleBroadcastListener desregistrado.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao desregistrar RoleBroadcastListener.", e);
            }
        }

        // <<< CORREÇÃO: Parar o KickHandler (é seguro chamar shutdown mesmo que não inicializado) >>>
        try {
            RoleKickHandler.shutdown();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao chamar shutdown() no RoleKickHandler.", e);
        }

        try {
            ServiceRegistry.getInstance().unregisterService(RoleService.class);
            logger.info("RoleService desregistrado.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao desregistrar RoleService.", e);
        }

        if (roleService != null) {
            try {
                roleService.shutdown();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao chamar shutdown() no RoleService.", e);
            }
        }

        this.roleService = null;
        this.roleSyncListener = null;
        this.roleBroadcastListener = null;

        logger.info("RoleModule (v2) finalizado.");
    }

    /**
     * Cria a implementação do PlatformKicker (baseado na plataforma detectada).
     */
    private RoleKickHandler.PlatformKicker createPlatformKicker() {
        Optional<ProxyServer> proxyOpt = ServiceRegistry.getInstance().getService(ProxyServer.class);
        if (proxyOpt.isPresent()) {
            ProxyServer proxyServer = proxyOpt.get();
            logger.fine("Detectado Velocity. Criando PlatformKicker para Velocity.");
            return (uuid, formattedKickMessage) -> {
                proxyServer.getPlayer(uuid).ifPresent(player -> {
                    net.kyori.adventure.text.Component kickComponent = MiniMessage.miniMessage().deserialize(formattedKickMessage);
                    player.disconnect(kickComponent);
                    logger.fine("Kick (Velocity) executado para: " + uuid);
                });
            };
        }

        Optional<Plugin> pluginOpt = ServiceRegistry.getInstance().getService(Plugin.class);
        if (pluginOpt.isPresent()) {
            Plugin plugin = pluginOpt.get();
            logger.fine("Detectado Spigot. Criando PlatformKicker para Spigot.");
            return (uuid, formattedKickMessage) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.kickPlayer(formattedKickMessage);
                        logger.fine("Kick (Spigot) executado para: " + uuid);
                    }
                });
            };
        }

        return null;
    }

    /**
     * Cria a implementação do RoleBroadcastListener (baseado na plataforma detectada).
     */
    private RedisMessageListener createBroadcastListener() {
        if (ServiceRegistry.getInstance().hasService(ProxyServer.class)) {
            logger.fine("Detectado Velocity. Criando RoleBroadcastListener para Velocity.");
            return new com.realmmc.controller.proxy.listeners.RoleBroadcastListener();
        }

        if (ServiceRegistry.getInstance().hasService(Plugin.class)) {
            logger.fine("Detectado Spigot. Criando RoleBroadcastListener para Spigot.");
            return new com.realmmc.controller.spigot.listeners.RoleBroadcastListener();
        }

        return null;
    }
}