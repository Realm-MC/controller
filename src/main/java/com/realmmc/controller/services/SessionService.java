package com.realmmc.controller.services;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.shared.utils.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SessionService implements Listener {

    private final Logger logger;
    private final RoleService roleService;
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;

    public SessionService(Logger logger) {
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
            if (sessionTrackerServiceOpt.isEmpty()) {
                logger.warning("SessionTrackerService não encontrado no SessionService!");
            }
            logger.info("SessionService inicializado (Correção de Thread-Blocking Aplicada).");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico: Serviço dependente não encontrado.", e);
            throw new RuntimeException("Falha ao inicializar SessionService.", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getUniqueId();
        String playerName = event.getName();

        logger.finer("[SessionService] AsyncPreLogin: Iniciando carregamento de dados para " + playerName);

        roleService.startPreLoadingPlayerData(uuid);

        try {
            Optional<CompletableFuture<PlayerSessionData>> futureOpt = roleService.getPreLoginFuture(uuid);

            if (futureOpt.isPresent()) {
                futureOpt.get().get(10, TimeUnit.SECONDS);
                logger.finer("[SessionService] Dados carregados com sucesso no AsyncPreLogin para " + playerName);
            } else {
                throw new IllegalStateException("Futuro de carregamento não encontrado.");
            }

        } catch (TimeoutException e) {
            logger.warning("[SessionService] Timeout ao carregar perfil de " + playerName + ". Cancelando conexão.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Messages.translate(MessageKey.KICK_PROFILE_TIMEOUT));
            cleanupSession(uuid, playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SessionService] Erro ao carregar dados de " + playerName, e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Messages.translate(MessageKey.KICK_PROFILE_ERROR));
            cleanupSession(uuid, playerName);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLoginCheck(PlayerLoginEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();

        Optional<PlayerSessionData> sessionData = roleService.getSessionDataFromCache(uuid);

        if (sessionData.isPresent()) {

            TaskScheduler.runAsync(() -> {
                sessionTrackerServiceOpt.ifPresent(service ->
                        service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE)
                );
            });

            logger.finer("[SessionService] Login permitido para " + playerName + " (Dados em cache).");
        } else {
            logger.warning("[SessionService] Jogador " + playerName + " tentou entrar sem dados em cache! Kickando.");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    Messages.translate(MessageKey.KICK_PROFILE_UNEXPECTED));

            cleanupSession(uuid, playerName);
        }

        roleService.removePreLoginFuture(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
    }

    private void cleanupSession(UUID uuid, String name) {
        sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, name));
        roleService.removePreLoginFuture(uuid);
        roleService.invalidateSession(uuid);
    }
}