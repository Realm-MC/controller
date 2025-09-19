package com.realmmc.controller.velocity.extensions;

import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.RawMessage;
import com.velocitypowered.api.proxy.Player;

public class PlayerExtensions {

    public static void msg(Player player, String message) {
        Messages.send(player, message);
    }

    public static void msg(Player player, MessageKey key) {
        Messages.send(player, key);
    }

    public static void msg(Player player, RawMessage message) {
        Messages.send(player, message);
    }

    public static void success(Player player, String message) {
        Messages.success(player, message);
    }

    public static void error(Player player, String message) {
        Messages.error(player, message);
    }

    public static void warning(Player player, String message) {
        Messages.warning(player, message);
    }

    public static void info(Player player, String message) {
        Messages.info(player, message);
    }
}