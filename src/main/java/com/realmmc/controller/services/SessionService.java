package com.realmmc.controller.services;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
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

/**
 * Gerencia o ciclo de vida da sessão de permissões do jogador (Spigot).
 * Dispara o pré-carregamento e garante que os dados estejam prontos no login.
 * Limpa o cache no logout.
 */
public class SessionService implements Listener {

    private final Logger logger;
    private final RoleService roleService;

    // Construtor que obtém o RoleService via ServiceRegistry
    public SessionService(Logger logger) {
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            logger.info("SessionService inicializado e conectado ao RoleService.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico: RoleService não encontrado ao iniciar SessionService! Funcionalidade comprometida.", e);
            // Lançar exceção para impedir o carregamento do plugin se RoleService for essencial
            throw new RuntimeException("Falha ao inicializar SessionService: RoleService ausente.", e);
        }
    }

    /**
     * Ouve o pré-login ASSÍNCRONO para INICIAR o carregamento das permissões.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        // Inicia o pré-carregamento apenas se o login for permitido até agora
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            UUID uuid = event.getUniqueId();
            logger.finer("[SessionService] AsyncPreLogin: Iniciando pré-carregamento para " + uuid);
            // Delega para o RoleService
            roleService.startPreLoadingPlayerData(uuid);
        } else {
            // Se o login já foi negado, limpa qualquer futuro pendente
            roleService.removePreLoginFuture(event.getUniqueId());
        }
    }

    /**
     * Ouve o evento de Login (SÍNCRONO, após AsyncPreLogin) para ESPERAR
     * o pré-carregamento e garantir que os dados estão no cache ANTES
     * que outros plugins/injeção de permissão precisem deles.
     */
    @EventHandler(priority = EventPriority.LOWEST) // Roda bem cedo no processo de login
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName(); // Para logs mais claros

        // Só processa se o login for permitido
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            logger.finer("[SessionService] Login negado para " + playerName + ". Limpando futuro de pré-login.");
            roleService.removePreLoginFuture(uuid); // Limpa o futuro se o login falhar
            return;
        }

        logger.finer("[SessionService] PlayerLogin: Aguardando conclusão do pré-carregamento para " + playerName);
        try {
            // Tenta obter o futuro do pré-carregamento
            Optional<CompletableFuture<PlayerSessionData>> futureOpt = roleService.getPreLoginFuture(uuid);

            if (futureOpt.isPresent()) {
                try {
                    // Bloqueia SÍNCRONAMENTE aqui para esperar o cálculo terminar
                    // Adiciona um timeout razoável para evitar travar a thread principal indefinidamente
                    futureOpt.get().get(10, TimeUnit.SECONDS); // Timeout de 10 segundos
                    logger.finer("[SessionService] Pré-carregamento concluído para " + playerName);
                } catch (TimeoutException te) {
                    logger.log(Level.SEVERE, "[SessionService] Timeout esperando pré-carregamento para " + playerName + "! Kickando...");
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cTimeout ao carregar seus dados. Tente novamente.");
                } catch (Exception e) { // Captura InterruptedException, ExecutionException, CancellationException
                    Throwable cause = (e instanceof ExecutionException || e instanceof CompletionException) ? e.getCause() : e;
                    logger.log(Level.SEVERE, "[SessionService] Erro ao esperar pré-carregamento para " + playerName, cause);
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cErro ao carregar seus dados de permissão.");
                }
            } else {
                // Se o futuro não existia, carrega sincronamente AGORA
                logger.warning("[SessionService] Futuro de pré-login não encontrado para " + playerName + ". Carregando permissões sincronamente...");
                try {
                    // Chama o método async e bloqueia com timeout
                    roleService.loadPlayerDataAsync(uuid).get(10, TimeUnit.SECONDS);
                    logger.info("[SessionService] Carregamento síncrono concluído para " + playerName);
                } catch (TimeoutException te) {
                    logger.log(Level.SEVERE, "[SessionService] Timeout no carregamento síncrono para " + playerName + "! Kickando...");
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cTimeout ao carregar seus dados (sync). Tente novamente.");
                } catch (Exception e) {
                    Throwable cause = (e instanceof ExecutionException || e instanceof CompletionException) ? e.getCause() : e;
                    logger.log(Level.SEVERE, "[SessionService] Erro no carregamento síncrono para " + playerName, cause);
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cErro crítico ao carregar seus dados de permissão.");
                }
            }
        } finally {
            // Garante que o futuro seja removido após ser consumido ou em caso de erro
            roleService.removePreLoginFuture(uuid);
        }
        // Se chegou aqui sem kick, o processo foi ok
        logger.finer("[SessionService] Processamento de login concluído para " + playerName);
    }


    /**
     * Ouve o evento de Quit (SÍNCRONO) para limpar o cache de sessão.
     */
    @EventHandler(priority = EventPriority.MONITOR) // Roda por último
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        logger.finer("[SessionService] PlayerQuit: Invalidando sessão para " + player.getName());
        // Delega a invalidação para o RoleService
        roleService.invalidateSession(uuid); // Limpa cache de sessão e futuro de pré-login
    }

    // Opcional: Adicionar handler para PlayerKickEvent também chamando invalidateSession
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onKick(PlayerKickEvent event) {
    //     roleService.invalidateSession(event.getPlayer().getUniqueId());
    // }
}