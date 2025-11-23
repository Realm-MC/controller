package com.realmmc.controller.shared.auth;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.session.SessionTrackerService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuthenticationGuard {

    private static final Logger LOGGER = Logger.getLogger(AuthenticationGuard.class.getName());

    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_ONLINE = "ONLINE";
    public static final MessageKey NOT_AUTHENTICATED_MESSAGE_KEY = MessageKey.AUTH_STILL_CONNECTING;

    public enum PlayerState {
        OFFLINE,
        CONNECTING,
        ONLINE
    }

    private AuthenticationGuard() {}

    private static Optional<SessionTrackerService> getSessionTracker() {
        return ServiceRegistry.getInstance().getService(SessionTrackerService.class);
    }

    public static PlayerState getPlayerState(UUID uuid) {
        if (uuid == null) return PlayerState.OFFLINE;

        return getSessionTracker()
                .flatMap(service -> service.getSessionField(uuid, "state"))
                .map(stateStr -> {
                    if (STATE_ONLINE.equals(stateStr)) return PlayerState.ONLINE;
                    if (STATE_CONNECTING.equals(stateStr)) return PlayerState.CONNECTING;
                    return PlayerState.OFFLINE;
                })
                .orElse(PlayerState.OFFLINE);
    }

    public static boolean isAuthenticated(UUID uuid) {
        return getPlayerState(uuid) == PlayerState.ONLINE;
    }

    public static boolean isConnecting(UUID uuid) {
        return getPlayerState(uuid) == PlayerState.CONNECTING;
    }

    public static Optional<String> checkCanInteractWith(UUID targetUuid) {
        PlayerState state = getPlayerState(targetUuid);

        if (state == PlayerState.ONLINE) {
            return Optional.empty();
        } else if (state == PlayerState.CONNECTING) {
            return Optional.of(Messages.translate(NOT_AUTHENTICATED_MESSAGE_KEY));
        } else {
            return Optional.of(Messages.translate(MessageKey.COMMON_PLAYER_NOT_ONLINE));
        }
    }
}