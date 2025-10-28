package com.realmmc.controller.spigot.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;

// Imports para Title (Adventure API)
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.time.Duration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener para o canal Redis ROLE_BROADCAST no Spigot.
 * Envia um Title global quando um jogador recebe um grupo.
 */
public class RoleBroadcastListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(RoleBroadcastListener.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RoleBroadcastListener() {
        LOGGER.info("RoleBroadcastListener (Spigot) inicializado.");
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

            Message titleMsg = Message.of(MessageKey.ROLE_BROADCAST_TITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName)
                    .with("player_color", playerColor)
                    .with("group_display", groupDisplay);
            String titleLine = Messages.translate(titleMsg); // Usa default (PT)

            Message subtitleMsg = Message.of(MessageKey.ROLE_BROADCAST_SUBTITLE)
                    .with("player_colored_name", playerColoredName)
                    .with("player_name", playerName)
                    .with("player_color", playerColor)
                    .with("group_display", groupDisplay);
            String subtitleLine = Messages.translate(subtitleMsg); // Usa default (PT)
            // <<< FIM CORREÇÃO >>>

            Component titleComponent = miniMessage.deserialize(titleLine);
            Component subtitleComponent = miniMessage.deserialize(subtitleLine);

            Title.Times times = Title.Times.times(Duration.ofMillis(1000), Duration.ofSeconds(3), Duration.ofMillis(1000));
            Title title = Title.title(titleComponent, subtitleComponent, times);

            // Itera sobre os jogadores (mais compatível com Spigot)
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
            }

            LOGGER.fine("Broadcast (Title) de role enviado: " + titleLine + " / " + subtitleLine);

        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Falha ao processar JSON da mensagem ROLE_BROADCAST: " + message, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao processar mensagem ROLE_BROADCAST: " + message, e);
        }
    }
}