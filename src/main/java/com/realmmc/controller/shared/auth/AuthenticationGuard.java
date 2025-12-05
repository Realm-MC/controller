package com.realmmc.controller.shared.auth;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;
import java.util.UUID;

public final class AuthenticationGuard {

    public static final String STATE_OFFLINE = "OFFLINE";
    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_REGISTER_REQUIRED = "REGISTER_REQUIRED";
    public static final String STATE_LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String STATE_ONLINE = "ONLINE";

    private AuthenticationGuard() {}

    private static Optional<SessionTrackerService> getSessionTracker() {
        return ServiceRegistry.getInstance().getService(SessionTrackerService.class);
    }

    public static Optional<MessageKey> validatePlayerReady(UUID uuid) {
        Optional<SessionTrackerService> tracker = getSessionTracker();
        if (tracker.isEmpty()) return Optional.empty();

        Optional<String> stateOpt = tracker.get().getSessionField(uuid, "state");

        if (stateOpt.isEmpty()) return Optional.empty();

        String state = stateOpt.get();

        if (state.equals(STATE_CONNECTING) ||
                state.equals(STATE_LOGIN_REQUIRED) ||
                state.equals(STATE_REGISTER_REQUIRED)) {
            return Optional.of(MessageKey.AUTH_STILL_CONNECTING);
        }

        return Optional.empty();
    }

    public static boolean isAuthenticated(UUID uuid) {
        return getSessionTracker()
                .flatMap(s -> s.getSessionField(uuid, "state"))
                .map(st -> st.equals(STATE_ONLINE))
                .orElse(false);
    }

    public static boolean isConnecting(UUID uuid) {
        return getSessionTracker()
                .flatMap(s -> s.getSessionField(uuid, "state"))
                .map(st -> st.equals(STATE_CONNECTING))
                .orElse(false);
    }

    public static Optional<String> checkCanInteractWith(UUID targetUuid) {
        return Optional.empty();
    }
}