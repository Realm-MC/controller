package com.realmmc.controller.proxy.permission;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
// <<< CORREÇÃO: Imports de Mensagens >>>
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
// <<< FIM CORREÇÃO >>>
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VelocityPermissionInjector {

    private final RoleService roleService;
    private final Logger logger;
    private final VelocityPermissionProvider provider;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Object pluginInstance;
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;

    public VelocityPermissionInjector(Object pluginInstance, Logger logger) {
        this.pluginInstance = pluginInstance;
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            this.provider = new VelocityPermissionProvider(logger);
            this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
            if (sessionTrackerServiceOpt.isEmpty()) {
                logger.warning("SessionTrackerService não encontrado no VelocityPermissionInjector!");
            }
            logger.info("Velocity permission injector preparado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro Crítico: Serviço dependente (RoleService ou SessionTrackerService) não encontrado ao criar VelocityPermissionInjector!", e);
            throw new RuntimeException("Falha ao inicializar VelocityPermissionInjector: Dependência(s) ausente(s).", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao criar VelocityPermissionInjector ou Provider!", e);
            throw new RuntimeException("Falha ao inicializar VelocityPermissionInjector.", e);
        }
    }

    /**
     * Ouve o PreLoginEvent, mas NÃO inicia mais o pré-carregamento.
     * Apenas limpa se o login for negado.
     */
    @Subscribe(order = com.velocitypowered.api.event.PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getUniqueId(), event.getUsername()));
        }
    }

    /**
     * Ouve o PermissionsSetupEvent (que roda DEPOIS de PostLoginEvent),
     * INICIA E ESPERA o carregamento, e injeta o provider.
     */
    @Subscribe(order = com.velocitypowered.api.event.PostOrder.NORMAL)
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        logger.finer("PermissionsSetupEvent para " + playerName + ". Iniciando carregamento síncrono, injetando provider e definindo estado ONLINE...");

        boolean loadSuccess = false;
        try {
            roleService.loadPlayerDataAsync(uuid).get(10, TimeUnit.SECONDS);
            logger.finer("Carregamento de permissões concluído para " + playerName);
            loadSuccess = true;


            if (loadSuccess) {
                event.setProvider(this.provider);
                logger.finer("VelocityPermissionProvider injetado para " + playerName);

                sessionTrackerServiceOpt.ifPresent(service ->
                        service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE)
                );
                logger.fine("Estado da sessão definido como ONLINE para " + playerName + " após setup de permissões.");
            }

        } catch (TimeoutException te) {
            logger.log(Level.SEVERE, "Timeout esperando/carregando permissões para " + playerName + ". Kickando...");
            // <<< CORREÇÃO: Usar tradução >>>
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_TIMEOUT));
            // <<< FIM CORREÇÃO >>>
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (CompletionException | ExecutionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            logger.log(Level.SEVERE, "Erro durante carregamento de permissões para " + playerName, cause);
            // <<< CORREÇÃO: Usar tradução >>>
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_ERROR));
            // <<< FIM CORREÇÃO >>>
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha geral injetando permissões Velocity para " + playerName, e);
            // <<< CORREÇÃO: Usar tradução >>>
            Component kickMessage = miniMessage.deserialize(Messages.translate(MessageKey.KICK_PROFILE_UNEXPECTED));
            // <<< FIM CORREÇÃO >>>
            if (player.isActive()) player.disconnect(kickMessage);
        } finally {
            if (!loadSuccess && player.isActive()) {
                logger.log(Level.SEVERE, "Carregamento/injeção de permissões falhou para {0}. Limpando sessão Redis.", playerName);
                sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, playerName));
            }
        }
    }

    /**
     * Ouve o DisconnectEvent para limpar o futuro (redundante).
     */
    @Subscribe(order = com.velocitypowered.api.event.PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
        // Nada mais é necessário aqui
    }
}