package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.UUID;
import java.util.logging.Logger;

@Listeners
public class PremiumLoginListener {

    private static final Logger LOGGER = Logger.getLogger("PremiumLoginListener");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String usernameLower = username.toLowerCase();

        boolean isClientOnlineMode = event.getUniqueId() != null;

        try {
            var premiumResult = PremiumChecker.checkPremium(username);
            boolean isUsernamePremium = premiumResult.premium();

            Proxy.getInstance().getPremiumLoginStatus().put(usernameLower, isUsernamePremium);

            if (isClientOnlineMode && isUsernamePremium) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                LOGGER.info(String.format("[Premium] '%s' autenticado com sucesso como premium.", username));
                return;
            }

            if (!isClientOnlineMode && isUsernamePremium) {
                String kickMessageString = """
                        <red><b>REALM MC</b></red><newline>
                        <white></white><newline>
                        <red>O usuário '<nickname>' pertence a uma conta de Minecraft Original, por favor utilize um launcher com login da Microsoft ou Mojang.</red>
                        """;
                Component kickMessage = MINI_MESSAGE.deserialize(kickMessageString, Placeholder.unparsed("nickname", username));

                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                LOGGER.warning(String.format("[Premium] '%s' foi bloqueado por tentar usar um nick premium num launcher não-original.", username));
                return;
            }

            if (!isClientOnlineMode && !isUsernamePremium) {
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
                Proxy.getInstance().getOfflineUuids().put(usernameLower, offlineUuid);

                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                LOGGER.info(String.format("[Premium] '%s' autenticado com sucesso como não-premium.", username));
                return;
            }

            if (isClientOnlineMode && !isUsernamePremium) {
                String kickMessageString = """
                        <red><b>REALM MC</b></red><newline>
                        <white></white><newline>
                        <red>O usuário '<nickname>' não pertence a uma conta de Minecraft Original, por favor utilize um launcher alternativo.</red>
                        """;
                Component kickMessage = MINI_MESSAGE.deserialize(kickMessageString, Placeholder.unparsed("nickname", username));

                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                LOGGER.warning(String.format("[Premium] '%s' foi bloqueado por uma falha de autenticação (launcher original, nick não-premium).", username));
            }

        } catch (Exception e) {
            Proxy.getInstance().getPremiumLoginStatus().put(usernameLower, false);
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            LOGGER.severe(String.format("[Premium] Ocorreu um erro crítico ao verificar a conta de '%s': %s", username, e.getMessage()));
        }
    }
}