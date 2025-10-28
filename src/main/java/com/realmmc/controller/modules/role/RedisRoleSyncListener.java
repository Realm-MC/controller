package com.realmmc.controller.modules.role;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.role.PermissionRefresher; // Import da interface
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;

import java.util.Objects;
import java.util.Optional; // Import Optional
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ouve o canal Redis ROLE_SYNC para invalidar caches de sessão locais
 * e notificar a plataforma para disparar recálculo/refresh.
 * (Anteriormente RoleSyncSubscriber)
 */
public class RedisRoleSyncListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RedisRoleSyncListener.class.getName());
    private final RedisSubscriber subscriber;
    private final RoleService roleService;

    // <<< CORREÇÃO PONTO 2: Remover o PermissionRefresher do construtor >>>
    // private final Optional<PermissionRefresher> permissionRefresherOpt;

    public RedisRoleSyncListener() {
        try {
            this.subscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);

            // <<< CORREÇÃO PONTO 2: Remover busca do construtor >>>
            // this.permissionRefresherOpt = ServiceRegistry.getInstance().getService(PermissionRefresher.class);
            // if (permissionRefresherOpt.isEmpty()) {
            //     LOGGER.warning("[RoleSync] PermissionRefresher não encontrado...!"); // Este log agora é inútil aqui
            // }

            LOGGER.info("RedisRoleSyncListener inicializado.");
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Erro crítico ao inicializar RedisRoleSyncListener: Dependência ausente!", e);
            throw new RuntimeException("Falha ao inicializar RedisRoleSyncListener: Dependência(s) ausente(s).", e);
        }
    }

    public void startListening() {
        try {
            subscriber.registerListener(RedisChannel.ROLE_SYNC, this);
            LOGGER.info("RedisRoleSyncListener registrado no canal Redis ROLE_SYNC.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha crítica ao registrar listener no RedisSubscriber para ROLE_SYNC!", e);
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_SYNC.getName().equals(channel) || message == null || message.isBlank()) {
            return;
        }

        UUID playerUuid = null;
        try {
            playerUuid = UUID.fromString(message.trim());
            final UUID finalUuid = playerUuid;

            // 1. Invalida o CACHE DE SESSÃO local
            roleService.invalidateSession(finalUuid);
            LOGGER.log(Level.FINE, "[RoleSync] Cache de SESSÃO invalidado para UUID {0} via ROLE_SYNC.", finalUuid);

            // <<< CORREÇÃO PONTO 2: Buscar o Refresher AQUI, dentro do onMessage >>>
            Optional<PermissionRefresher> permissionRefresherOpt = ServiceRegistry.getInstance().getService(PermissionRefresher.class);
            // <<< FIM CORREÇÃO >>>

            // 2. Chama o PermissionRefresher (se disponível)
            permissionRefresherOpt.ifPresentOrElse(
                    refresher -> {
                        try {
                            refresher.refreshPlayerPermissions(finalUuid);
                            LOGGER.finer("[RoleSync] Pedido de refresh/recálculo enviado para PermissionRefresher para UUID " + finalUuid);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "[RoleSync] Erro ao executar PermissionRefresher para UUID " + finalUuid, e);
                        }
                    },
                    // Loga um AVISO apenas se não encontrar o refresher
                    () -> LOGGER.warning("[RoleSync] PermissionRefresher não encontrado - o cache será invalidado, mas o refresh em tempo real não ocorrerá para " + finalUuid)
            );

        } catch (IllegalArgumentException e) {
            LOGGER.warning("[RoleSync] Mensagem UUID inválida recebida no canal ROLE_SYNC: '" + message + "'");
        } catch (Exception e) {
            UUID currentUuid = playerUuid;
            LOGGER.log(Level.SEVERE, "[RoleSync] Erro inesperado ao processar mensagem (UUID: " + (currentUuid != null ? currentUuid : message) + ")", e);
        }
    }

    public void stopListening() {
        try {
            subscriber.unregisterListener(RedisChannel.ROLE_SYNC);
            LOGGER.info("RedisRoleSyncListener desregistrado do canal Redis ROLE_SYNC.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao desregistrar RedisRoleSyncListener do Redis.", e);
        }
        LOGGER.info("RedisRoleSyncListener stopListening() finalizado.");
    }
}