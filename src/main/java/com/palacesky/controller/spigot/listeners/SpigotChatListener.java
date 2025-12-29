package com.palacesky.controller.spigot.listeners;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.chat.ChatService;
import com.palacesky.controller.shared.utils.NicknameFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@Listeners
public class SpigotChatListener implements Listener {

    private final ChatService chatService;

    public SpigotChatListener() {
        this.chatService = ServiceRegistry.getInstance().requireService(ChatService.class);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        event.setCancelled(true);

        String formattedName = NicknameFormatter.getNickname(player.getUniqueId(), true);

        chatService.sendMessage(
                "local",
                player.getUniqueId(),
                player.getName(),
                formattedName,
                message,
                "Spigot-Local"
        );
    }
}