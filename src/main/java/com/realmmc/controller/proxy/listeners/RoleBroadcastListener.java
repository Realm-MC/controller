package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.velocitypowered.api.proxy.ProxyServer;

// Imports para Title (Adventure API)
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import java.time.Duration; // Importar Duration

import java.util.logging.Level;
import java.util.logging.Logger;
import com.velocitypowered.api.proxy.Player; // Import Player

/**
 * Listener para o canal Redis ROLE_BROADCAST no Velocity.
 * Envia um Title global quando um jogador recebe um grupo.
 */
public class RoleBroadcastListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RoleBroadcastListener.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ProxyServer proxyServer;

    public RoleBroadcastListener() {
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        LOGGER.info("RoleBroadcastListener (Proxy) inicializado.");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_BROADCAST.getName().equals(channel)) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(message);
            String playerName = node.path("playerName").asText("Alguém");
            String playerColor = node.path("playerColor").asText("<white>");
            String groupDisplay = node.path("groupDisplay").asText("um grupo");

            // <<< CORREÇÃO PONTO 4: Montar placeholder e passar para AMBOS os translates >>>
            String playerColoredName = playerColor + playerName; // Placeholder combinado

            // Cria a mensagem para o Título
            Message titleMsg = Message.of(MessageKey.ROLE_BROADCAST_TITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName) // Adiciona nome sem cor
                    .with("player_color", playerColor) // Adiciona cor pura
                    .with("group_display", groupDisplay); // Adiciona grupo
            // Traduz (usando locale default PT_BR)
            String titleLine = Messages.translate(titleMsg);

            // Cria a mensagem para o Subtítulo
            Message subtitleMsg = Message.of(MessageKey.ROLE_BROADCAST_SUBTITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName)
                    .with("player_color", playerColor)
                    .with("group_display", groupDisplay);
            // Traduz (usando locale default PT_BR)
            String subtitleLine = Messages.translate(subtitleMsg);
            // <<< FIM CORREÇÃO >>>

            Component titleComponent = miniMessage.deserialize(titleLine);
            Component subtitleComponent = miniMessage.deserialize(subtitleLine);

            Title.Times times = Title.Times.times(Duration.ofMillis(1000), Duration.ofSeconds(3), Duration.ofMillis(1000));
            Title title = Title.title(titleComponent, subtitleComponent, times);

            proxyServer.getAllPlayers().forEach(player -> player.showTitle(title));

            LOGGER.fine("Broadcast (Title) de role enviado: " + titleLine + " / " + subtitleLine);

        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Falha ao processar JSON da mensagem ROLE_BROADCAST: " + message, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao processar mensagem ROLE_BROADCAST: " + message, e);
        }
    }
}