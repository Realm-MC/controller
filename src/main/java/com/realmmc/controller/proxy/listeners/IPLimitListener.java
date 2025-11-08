package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

@Listeners
public class IPLimitListener {

    private static final int MAX_ACCOUNTS_PER_IP = 3;
    private final SessionTrackerService sessionTrackerService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Logger logger = Logger.getLogger(IPLimitListener.class.getName());

    public IPLimitListener() {
        this.sessionTrackerService = ServiceRegistry.getInstance().getService(SessionTrackerService.class)
                .orElseThrow(() -> new IllegalStateException("SessionTrackerService não encontrado para IPLimitListener!"));
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }

        InetSocketAddress address = event.getConnection().getRemoteAddress();
        if (address == null || address.getAddress() == null) {
            logger.warning("[IPLimit] Endereço IP do jogador " + event.getUsername() + " é nulo. A permitir conexão.");
            return;
        }

        String ip = address.getAddress().getHostAddress();

        long activeSessions = sessionTrackerService.getActiveSessionsCountByIp(ip);

        if (activeSessions >= MAX_ACCOUNTS_PER_IP) {
            String translatedKick = Messages.translate(
                    Message.of(MessageKey.KICK_IP_LIMIT).with("limit", MAX_ACCOUNTS_PER_IP));

            Component kickMessage = miniMessage.deserialize(translatedKick);

            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
            logger.info("[IPLimit] Bloqueado o jogador " + event.getUsername() + " do IP " + ip +
                    " por exceder o limite de " + MAX_ACCOUNTS_PER_IP + " contas ativas.");
        }
    }
}