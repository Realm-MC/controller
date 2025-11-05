package com.realmmc.controller.shared.auth;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.session.SessionTrackerService; // Importar o serviço
import com.realmmc.controller.shared.profile.Profile; // Importar Profile
import com.realmmc.controller.shared.profile.ProfileService; // Importar ProfileService

// <<< CORREÇÃO: Imports de Mensagens >>>
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
// <<< FIM CORREÇÃO >>>

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário para verificar o estado de conexão/autenticação de um jogador
 * consultando o estado armazenado no SessionTrackerService (Redis).
 */
public final class AuthenticationGuard {

    private static final Logger LOGGER = Logger.getLogger(AuthenticationGuard.class.getName());

    // Constantes para os estados armazenados no Redis
    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_ONLINE = "ONLINE";

    // <<< CORREÇÃO: Usar MessageKey em vez de String >>>
    public static final MessageKey NOT_AUTHENTICATED_MESSAGE_KEY = MessageKey.AUTH_STILL_CONNECTING;
    // <<< FIM CORREÇÃO >>>

    // Enum para representar os possíveis estados publicamente
    public enum PlayerState {
        OFFLINE,      // Sessão não encontrada no Redis
        CONNECTING,   // Estado = "CONNECTING"
        ONLINE        // Estado = "ONLINE"
    }

    // Construtor privado para impedir instanciação
    private AuthenticationGuard() {}

    /**
     * Obtém a instância do SessionTrackerService do ServiceRegistry.
     * Usa um cache simples para evitar lookups repetidos no ServiceRegistry.
     */
    private static class SessionTrackerHolder {
        static volatile Optional<SessionTrackerService> serviceOpt = Optional.empty();
        static volatile long lastCheckTime = 0;
        static final long CHECK_INTERVAL_MS = 5000;

        static synchronized Optional<SessionTrackerService> getService() {
            long now = System.currentTimeMillis();
            if (serviceOpt.isEmpty() && (now - lastCheckTime > CHECK_INTERVAL_MS)) {
                lastCheckTime = now;
                serviceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
                if (serviceOpt.isEmpty()) {
                    LOGGER.log(Level.SEVERE, "AuthenticationGuard: SessionTrackerService não encontrado no ServiceRegistry!");
                } else {
                    LOGGER.info("AuthenticationGuard: SessionTrackerService encontrado e cacheado.");
                }
            }
            return serviceOpt;
        }
    }

    private static Optional<SessionTrackerService> getSessionTracker() {
        return SessionTrackerHolder.getService();
    }

    /**
     * Verifica se um jogador está totalmente online e pronto para interagir.
     *
     * @param uuid O UUID do jogador.
     * @return true se o estado for "ONLINE", false caso contrário.
     */
    public static boolean isAuthenticated(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return getSessionTracker()
                .flatMap(service -> service.getSessionField(uuid, "state"))
                .map(STATE_ONLINE::equals)
                .orElse(false);
    }

    /**
     * Verifica se um jogador está na fase de conexão.
     *
     * @param uuid O UUID do jogador.
     * @return true se o estado for "CONNECTING", false caso contrário.
     */
    public static boolean isConnecting(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return getSessionTracker()
                .flatMap(service -> service.getSessionField(uuid, "state"))
                .map(STATE_CONNECTING::equals)
                .orElse(false);
    }

    /**
     * Obtém o estado atual do jogador (OFFLINE, CONNECTING, ONLINE).
     *
     * @param uuid O UUID do jogador.
     * @return O enum PlayerState correspondente.
     */
    public static PlayerState getPlayerState(UUID uuid) {
        if (uuid == null) {
            return PlayerState.OFFLINE;
        }
        return getSessionTracker()
                .flatMap(service -> service.getSessionField(uuid, "state"))
                .map(stateStr -> {
                    if (STATE_ONLINE.equals(stateStr)) {
                        return PlayerState.ONLINE;
                    } else if (STATE_CONNECTING.equals(stateStr)) {
                        return PlayerState.CONNECTING;
                    } else {
                        LOGGER.log(Level.WARNING, "Estado de sessão desconhecido ''{0}'' encontrado para UUID {1}", new Object[]{stateStr, uuid});
                        return PlayerState.OFFLINE;
                    }
                })
                .orElse(PlayerState.OFFLINE);
    }

    /**
     * Verifica se é seguro interagir com um jogador (estado ONLINE).
     *
     * @param targetUuid O UUID do jogador alvo da interação.
     * @return Optional contendo a mensagem de erro se o jogador não estiver ONLINE,
     * ou Optional.empty() se a interação for permitida.
     */
    public static Optional<String> checkCanInteractWith(UUID targetUuid) {
        PlayerState state = getPlayerState(targetUuid);
        if (state == PlayerState.ONLINE) {
            return Optional.empty();
        } else {
            // <<< CORREÇÃO: Usar Messages.translate() >>>
            return Optional.of(Messages.translate(NOT_AUTHENTICATED_MESSAGE_KEY));
            // <<< FIM CORREÇÃO >>>
        }
    }

    /**
     * Helper para verificar interação baseado no username.
     *
     * @param targetUsername O username do jogador alvo.
     * @return Optional contendo a mensagem de erro ou Optional.empty().
     */
    public static Optional<String> checkCanInteractWith(String targetUsername) {
        if (targetUsername == null || targetUsername.isEmpty()) {
            // <<< CORREÇÃO: Usar Messages.translate() >>>
            return Optional.of(Messages.translate(MessageKey.AUTH_INVALID_PLAYER));
            // <<< FIM CORREÇÃO >>>
        }
        Optional<UUID> uuidOpt = ServiceRegistry.getInstance().getService(ProfileService.class)
                .flatMap(ps -> ps.getByUsername(targetUsername.toLowerCase()))
                .map(Profile::getUuid);

        if (uuidOpt.isEmpty()) {
            // Tentar buscar no SessionTrackerService pelo username se o profile não foi encontrado rapidamente?
            // Por agora, vamos assumir que se não tem profile, não pode interagir.
            // <<< CORREÇÃO: Usar Messages.translate() >>>
            return Optional.of(Messages.translate(NOT_AUTHENTICATED_MESSAGE_KEY)); // Ou "Jogador não encontrado."
            // <<< FIM CORREÇÃO >>>
        }

        return checkCanInteractWith(uuidOpt.get());
    }
}