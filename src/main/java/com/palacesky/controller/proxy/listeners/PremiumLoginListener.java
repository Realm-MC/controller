package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Listeners
public class PremiumLoginListener {

    private static final Logger LOGGER = Logger.getLogger(PremiumLoginListener.class.getName());
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Subscribe(order = PostOrder.LATE)
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String usernameLower = username.toLowerCase();

        Optional<Player> existingPlayer = Proxy.getInstance().getServer().getPlayer(username);
        if (existingPlayer.isPresent()) {
            String translatedKick = Messages.translate(MessageKey.KICK_ALREADY_CONNECTED);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(MINI_MESSAGE.deserialize(translatedKick)));
            LOGGER.warning(String.format("[Premium] '%s' was blocked from duplicate connection.", username));
            return;
        }

        try {
            var premiumResult = PremiumChecker.checkPremium(username);

            if (premiumResult.reason() != null) {
                LOGGER.severe(String.format("[Premium] Mojang API failed to verify '%s'. Reason: %s", username, premiumResult.reason()));
                String translatedKick = Messages.translate(Message.of(MessageKey.KICK_PREMIUM_AUTH_FAILED));
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(MINI_MESSAGE.deserialize(translatedKick)));
                return;
            }

            boolean isUsernamePremium = premiumResult.premium();
            Proxy.getInstance().getPremiumLoginStatus().put(usernameLower, isUsernamePremium);

            if (isUsernamePremium) {
                UUID realPremiumUuid = premiumResult.profile().getId();
                UUID clientReportedUuid = event.getUniqueId();

                if (clientReportedUuid != null && clientReportedUuid.equals(realPremiumUuid)) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                    LOGGER.info(String.format("[Premium] '%s' successfully authenticated as premium.", username));
                } else {
                    String translatedKick = Messages.translate(
                            Message.of(MessageKey.KICK_PREMIUM_NICKNAME)
                                    .with("nickname", username)
                    );

                    Component kickMessage = MINI_MESSAGE.deserialize(translatedKick);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                    LOGGER.warning(String.format("[Premium] '%s' blocked for using premium nick on a non-original launcher (UUID Mismatch: Client: %s, Mojang: %s).",
                            username, clientReportedUuid, realPremiumUuid));
                }
            } else {
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
                Proxy.getInstance().getOfflineUuids().put(usernameLower, offlineUuid);

                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                LOGGER.info(String.format("[Premium] '%s' successfully authenticated as non-premium.", username));
            }
        } catch (Exception e) {
            Proxy.getInstance().getPremiumLoginStatus().put(usernameLower, false);
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            LOGGER.severe(String.format("[Premium] A critical error occurred while verifying the account of '%s': %s", username, e.getMessage()));
        }
    }
}