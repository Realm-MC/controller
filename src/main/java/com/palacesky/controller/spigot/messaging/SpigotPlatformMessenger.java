package com.palacesky.controller.spigot.messaging;

import com.palacesky.controller.shared.messaging.MessagingSDK;
import com.palacesky.controller.shared.messaging.PlatformMessenger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SpigotPlatformMessenger implements PlatformMessenger {

    private static final MessagingSDK SDK = MessagingSDK.getInstance();

    @Override
    public boolean isPlayerInstance(Object any) {
        return any instanceof Player;
    }

    @Override
    public void send(Object player, String message) {
        if (isPlayerInstance(player)) {
            SDK.sendRawMessage(player, message);
        }
    }

    @Override
    public void sendMany(Iterable<Object> players, String message) {
        for (Object player : players) {
            if (isPlayerInstance(player)) {
                send(player, message);
            }
        }
    }

    @Override
    public void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, message);
        }
    }
}