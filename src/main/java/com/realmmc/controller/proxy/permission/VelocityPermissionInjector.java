package com.realmmc.controller.proxy.permission;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData; // Import correto
import com.realmmc.controller.modules.role.RoleService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent; // Correto
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.proxy.Player;
// Importa o VelocityPermissionProvider (nossa implementação)
import com.realmmc.controller.proxy.permission.VelocityPermissionProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException; // Import necessário
import java.util.concurrent.TimeUnit; // Import necessário
import java.util.concurrent.TimeoutException; // Import necessário
import java.util.logging.Level;
import java.util.logging.Logger;

public class VelocityPermissionInjector {

    private final RoleService roleService;
    private final Logger logger;
    private final VelocityPermissionProvider provider; // Nossa implementação do provider
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Object pluginInstance; // Instância do plugin Velocity (@Plugin)

    public VelocityPermissionInjector(Object pluginInstance, Logger logger) {
        this.pluginInstance = pluginInstance;
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
            // Cria o provider customizado passando o logger
            this.provider = new VelocityPermissionProvider(logger);
            logger.info("Velocity permission injector preparado.");
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro Crítico: RoleService não encontrado ao criar VelocityPermissionInjector!", e);
            throw new RuntimeException("Falha ao inicializar VelocityPermissionInjector: RoleService ausente.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro inesperado ao criar VelocityPermissionInjector ou Provider!", e);
            throw new RuntimeException("Falha ao inicializar VelocityPermissionInjector.", e);
        }
    }

    /**
     * Ouve o PreLoginEvent para iniciar o pré-carregamento.
     */
    @Subscribe(order = com.velocitypowered.api.event.PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        // Delega para o RoleService
        roleService.startPreLoadingPlayerData(event.getUniqueId());
        logger.finer("PreLogin: Disparado pré-carregamento para " + event.getUsername());
    }

    /**
     * Ouve o PermissionsSetupEvent para esperar o pré-load e injetar o provider.
     */
    @Subscribe
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        logger.finer("PermissionsSetupEvent para " + playerName + ". Aguardando pré-load e injetando provider...");

        try {
            Optional<CompletableFuture<PlayerSessionData>> futureOpt = roleService.getPreLoginFuture(uuid);

            if (futureOpt.isPresent()) {
                // Espera o pré-load terminar (com timeout)
                futureOpt.get().get(10, TimeUnit.SECONDS); // Timeout de 10s
                logger.finer("Pré-load concluído para " + playerName);
            } else {
                logger.warning("Futuro de pré-load não encontrado para " + playerName + ". Carregando sync...");
                // Carrega sincronamente como fallback (com timeout)
                roleService.loadPlayerDataAsync(uuid).get(10, TimeUnit.SECONDS);
                logger.info("Carregamento síncrono concluído para " + playerName + " em PermissionsSetup.");
            }

            // Injeta a nossa instância do Provider customizado
            event.setProvider(this.provider);
            logger.finer("VelocityPermissionProvider injetado para " + playerName);

        } catch (TimeoutException te) {
            logger.log(Level.SEVERE, "Timeout esperando/carregando permissões para " + playerName + ". Kickando...");
            Component kickMessage = miniMessage.deserialize("<red>Timeout ao carregar seus dados. Tente novamente.</red>");
            // Verifica se o jogador ainda está ativo antes de kickar
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (CompletionException | ExecutionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            logger.log(Level.SEVERE, "Erro durante carregamento de permissões para " + playerName, cause);
            Component kickMessage = miniMessage.deserialize("<red>Erro ao carregar suas permissões.</red>");
            if (player.isActive()) player.disconnect(kickMessage);
        } catch (Exception e) { // Inclui InterruptedException
            logger.log(Level.SEVERE, "Falha geral injetando permissões Velocity para " + playerName, e);
            Component kickMessage = miniMessage.deserialize("<red>Erro inesperado ao carregar permissões.</red>");
            if (player.isActive()) player.disconnect(kickMessage);
        } finally {
            // Garante limpeza do futuro em todos os casos (sucesso, erro, timeout)
            roleService.removePreLoginFuture(uuid);
        }
    }

    /**
     * Ouve o DisconnectEvent para limpar o cache de sessão.
     */
    @Subscribe(order = com.velocitypowered.api.event.PostOrder.LATE) // Roda mais tarde
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        // Invalida cache de sessão E futuro de pré-login
        roleService.invalidateSession(player.getUniqueId());
        logger.finer("Cache de sessão e futuro limpos para " + player.getUsername() + " no disconnect.");
    }
}