package com.palacesky.controller.spigot.services;

import com.palacesky.controller.shared.utils.NicknameFormatter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatService implements Listener {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        event.renderer((source, sourceDisplayName, message, viewer) -> {

            String playerFormat = NicknameFormatter.getNickname(source.getUniqueId(), true);
            Component playerComponent = miniMessage.deserialize(playerFormat);

            Component messageComponent;

            if (player.hasPermission("controller.champion")) {
                Component legacyParsed = legacyAmpersand.deserialize(plainMessage);
                String hybridMessage = miniMessage.serialize(legacyParsed);
                messageComponent = miniMessage.deserialize(hybridMessage)
                        .colorIfAbsent(NamedTextColor.WHITE);
            } else {
                messageComponent = Component.text(plainMessage)
                        .color(NamedTextColor.GRAY);
            }

            return playerComponent
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(messageComponent);
        });
    }
}