package com.realmmc.controller.modules.role;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
// Importar RedisRoleSyncListener explicitamente
import com.realmmc.controller.modules.role.RedisRoleSyncListener;
import com.realmmc.controller.shared.role.RoleKickHandler;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

// Imports específicos da plataforma (para createBroadcastListener)
import org.bukkit.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class RoleModule extends AbstractCoreModule {

    private RoleService roleService;
    private RedisRoleSyncListener roleSyncListener;
    private RedisMessageListener roleBroadcastListener;

    public RoleModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "RoleModule"; } // Nome consistente
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo de gerenciamento de grupos e permissões (v2)."; }

    // Depende de Profile (18), Database (10), Scheduler (15)
    @Override public String[] getDependencies() { return new String[]{"Database", "Profile", "SchedulerModule"}; }

    @Override public int getPriority() { return 25; }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando RoleService e componentes relacionados (v2)...");

        // 1. Instanciar RoleService (mas NÃO registrar ainda)
        try {
            // A instanciação pode falhar se ProfileService não estiver pronto (dependência)
            this.roleService = new RoleService(logger);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha crítica ao instanciar RoleService! Dependências não resolvidas?", e);
            throw e; // Impede o módulo de habilitar se RoleService não puder ser criado
        }

        // 2. Sincronizar Grupos Padrão PRIMEIRO
        try {
            roleService.setupDefaultRoles(); // Garante que 'default' exista antes de carregar
        } catch (Exception e) {
            // Logar como SEVERE pois isso pode indicar problema com DB
            logger.log(Level.SEVERE, "Erro SEVERO durante a sincronização inicial dos grupos padrão com MongoDB!", e);
            // Considerar lançar a exceção se a falha aqui for crítica
            throw new RuntimeException("Falha ao sincronizar roles padrão com MongoDB.", e);
        }

        // 3. AGORA registrar RoleService (depois de setupDefaultRoles)
        try {
            ServiceRegistry.getInstance().registerService(RoleService.class, roleService);
            logger.info("RoleService registrado com sucesso.");
        } catch (Exception e){
            logger.log(Level.SEVERE, "Falha inesperada ao registrar RoleService!", e);
            throw e;
        }


        // 4. Iniciar Listener de Sync (ROLE_SYNC)
        RedisSubscriber redisSubscriber = null;
        try {
            redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            this.roleSyncListener = new RedisRoleSyncListener(); // O construtor dele busca dependências
            roleSyncListener.startListening(); // Registra no subscriber compartilhado
            logger.info("RedisRoleSyncListener iniciado e escutando canal ROLE_SYNC.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao obter RedisSubscriber necessário para RoleModule! Sincronização de roles não funcionará.", e);
            // Não lançar exceção aqui permite que o RoleService funcione localmente, mas sem sync
        } catch (RuntimeException e) { // Captura RuntimeException do construtor de RedisRoleSyncListener
            logger.log(Level.SEVERE, "Falha ao instanciar RedisRoleSyncListener (dependência ausente?)! Sincronização de roles não funcionará.", e);
            this.roleSyncListener = null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha inesperada ao iniciar RedisRoleSyncListener!", e);
            this.roleSyncListener = null;
        }

        // 5. Inicializar RoleKickHandler (Será feito pelo Módulo de Plataforma)
        // A lógica foi movida para ProxyModule/SpigotModule onEnable

        // 6. Registrar Listener de Broadcast (ROLE_BROADCAST)
        if (redisSubscriber != null) {
            try {
                // Cria o listener apropriado baseado nos serviços registrados (ProxyServer ou Plugin)
                this.roleBroadcastListener = createBroadcastListener();
                if (this.roleBroadcastListener != null) {
                    redisSubscriber.registerListener(RedisChannel.ROLE_BROADCAST, this.roleBroadcastListener);
                    logger.info("RoleBroadcastListener registrado no canal ROLE_BROADCAST.");
                } else {
                    // Isso pode acontecer se o RoleModule carregar antes do SpigotModule/ProxyModule
                    logger.warning("RoleBroadcastListener não criado no onEnable (Plataforma ainda não detectada?).");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Falha ao registrar RoleBroadcastListener!", e);
                this.roleBroadcastListener = null;
            }
        } else {
            logger.severe("RedisSubscriber não encontrado, RoleBroadcastListener não registrado.");
        }

        logger.info("RoleModule (v2) habilitado."); // Removido "com sucesso" pois listeners podem ter falhado
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("Desabilitando RoleModule (v2)...");

        // Desregistra listeners Redis
        if (roleSyncListener != null) {
            try { roleSyncListener.stopListening(); }
            catch (Exception e) { logger.log(Level.WARNING, "Erro ao chamar stopListening() no RedisRoleSyncListener.", e); }
        }

        if (roleBroadcastListener != null) {
            try {
                // Tenta obter o subscriber novamente para desregistrar
                ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                        .ifPresent(sub -> {
                            try {
                                sub.unregisterListener(RedisChannel.ROLE_BROADCAST);
                                logger.info("RoleBroadcastListener desregistrado.");
                            } catch (Exception ex) {
                                logger.log(Level.WARNING, "Erro ao desregistrar RoleBroadcastListener.", ex);
                            }
                        });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao obter RedisSubscriber para desregistrar RoleBroadcastListener.", e);
            }
        }

        // Para o KickHandler (é seguro chamar shutdown mesmo que não inicializado)
        try {
            RoleKickHandler.shutdown();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao chamar shutdown() no RoleKickHandler.", e);
        }

        // Desregistra o RoleService
        try {
            ServiceRegistry.getInstance().unregisterService(RoleService.class);
            logger.info("RoleService desregistrado.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro ao desregistrar RoleService.", e);
        }

        // Chama shutdown interno do RoleService (limpar caches, parar tarefas)
        if (roleService != null) {
            try {
                roleService.shutdown();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao chamar shutdown() no RoleService.", e);
            }
        }

        // Limpa referências
        this.roleService = null;
        this.roleSyncListener = null;
        this.roleBroadcastListener = null;

        logger.info("RoleModule (v2) finalizado.");
    }

    /**
     * Cria a implementação do RoleBroadcastListener (baseado na plataforma detectada).
     * Retorna null se nenhuma plataforma for detectada ainda.
     */
    private RedisMessageListener createBroadcastListener() {
        ServiceRegistry registry = ServiceRegistry.getInstance();
        // Verifica Velocity primeiro
        if (registry.hasService(ProxyServer.class)) {
            logger.fine("Detectado Velocity. Criando RoleBroadcastListener para Velocity.");
            // Usar o fully qualified name para evitar conflito de import
            return new com.realmmc.controller.proxy.listeners.RoleBroadcastListener();
        }
        // Verifica Spigot
        else if (registry.hasService(Plugin.class)) {
            logger.fine("Detectado Spigot. Criando RoleBroadcastListener para Spigot.");
            // Usar o fully qualified name
            return new com.realmmc.controller.spigot.listeners.RoleBroadcastListener();
        }
        // Nenhuma plataforma detectada (pode ser cedo na inicialização)
        return null;
    }
}