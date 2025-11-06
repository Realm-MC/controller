package com.realmmc.controller.services;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.session.SessionTrackerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
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
            logger.info("SessionService inicializado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico: Serviço dependente (RoleService ou SessionTrackerService) não encontrado ao iniciar SessionService! Funcionalidade comprometida.", e);
            throw new RuntimeException("Falha ao inicializar SessionService: Dependência(s) ausente(s).", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            UUID uuid = event.getUniqueId();
            logger.finer("[SessionService] AsyncPreLogin: Iniciando pré-carregamento de roles para " + uuid);
            roleService.startPreLoadingPlayerData(uuid);
        } else {
            UUID uuid = event.getUniqueId();
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, event.getName()));
            roleService.removePreLoginFuture(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLoginWaitForLoadAndSetOnline(PlayerLoginEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();

        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            logger.finer("[SessionService] Login negado (HIGH) para " + playerName + ". Limpando futuro e sessão.");
            roleService.removePreLoginFuture(uuid);
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, playerName));
            return;
        }

        logger.finer("[SessionService] PlayerLogin (HIGH): Aguardando conclusão do pré-carregamento/carregamento sync para " + playerName);
        boolean loadSuccess = false;
        try {
            Optional<CompletableFuture<PlayerSessionData>> futureOpt = roleService.getPreLoginFuture(uuid);
            if (futureOpt.isPresent()) {
                try {
                    futureOpt.get().get(10, TimeUnit.SECONDS);
                    logger.finer("[SessionService] Pré-carregamento concluído (HIGH) para " + playerName);
                    loadSuccess = true;
                } catch (TimeoutException te) {
                    logger.log(Level.SEVERE, "[SessionService] Timeout (HIGH) esperando pré-carregamento para " + playerName + "! Kickando...");
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Messages.translate(MessageKey.KICK_PROFILE_TIMEOUT));
                } catch (Exception e) {
                    Throwable cause = (e instanceof ExecutionException || e instanceof CompletionException) ? e.getCause() : e;
                    logger.log(Level.SEVERE, "[SessionService] Erro (HIGH) ao esperar pré-carregamento para " + playerName, cause);
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Messages.translate(MessageKey.KICK_PROFILE_ERROR));
                }
            } else {
                if (roleService.getSessionDataFromCache(uuid).isPresent()) {
                    logger.log(Level.INFO, "[SessionService] Futuro não encontrado (HIGH) para {0}, mas dados já estão no cache. Assumindo sucesso.", playerName);
                    loadSuccess = true;
                } else {
                    logger.log(Level.SEVERE, "[SessionService] CRÍTICO (HIGH): Futuro de pré-login não encontrado E cache vazio para {0}! Kickando.", playerName);
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Messages.translate(MessageKey.KICK_PROFILE_UNEXPECTED));
                }
            }
        } finally {
            roleService.removePreLoginFuture(uuid);
            if (!loadSuccess && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                logger.log(Level.SEVERE, "[SessionService] Carregamento falhou (HIGH) mas evento não foi cancelado! Limpando sessão Redis para " + playerName);
                sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, playerName));
            } else if (loadSuccess) {
                sessionTrackerServiceOpt.ifPresent(service -> service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE));
                logger.fine("[SessionService] Estado da sessão definido como ONLINE para " + playerName);
            }
        }
        if(loadSuccess) logger.finer("[SessionService] Processamento de login (HIGH) concluído para " + playerName);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
    }
}