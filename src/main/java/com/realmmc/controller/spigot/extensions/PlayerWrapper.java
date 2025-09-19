package com.realmmc.controller.spigot.extensions;

import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.RawMessage;
import org.bukkit.entity.Player;

public class PlayerWrapper {
    private final Player player;
    
    private PlayerWrapper(Player player) {
        this.player = player;
    }

    public static PlayerWrapper wrap(Player player) {
        return new PlayerWrapper(player);
    }

    public PlayerWrapper msg(MessageKey key) {
        Messages.send(player, key);
        return this;
    }

    public PlayerWrapper msg(RawMessage message) {
        Messages.send(player, message);
        return this;
    }

    public PlayerWrapper msg(String message) {
        Messages.send(player, message);
        return this;
    }

    public PlayerWrapper success(String message) {
        Messages.success(player, message);
        return this;
    }

    public PlayerWrapper error(String message) {
        Messages.error(player, message);
        return this;
    }

    public PlayerWrapper warning(String message) {
        Messages.warning(player, message);
        return this;
    }

    public PlayerWrapper info(String message) {
        Messages.info(player, message);
        return this;
    }

    public Player getPlayer() {
        return player;
    }

    public String getName() {
        return player.getName();
    }
    
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }
    
    public void sendMessage(String message) {
        player.sendMessage(message);
    }
}