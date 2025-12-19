package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Listeners
public class ProxyLoginProtectionListener {

    private final List<String> allowedCommands = Arrays.asList(
            "login", "logar",
            "register", "registrar"
    );

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;

        Optional<ServerConnection> currentServer = player.getCurrentServer();
        if (currentServer.isEmpty()) return;

        String serverName = currentServer.get().getServerInfo().getName().toLowerCase();

        if (serverName.startsWith("login")) {

            String command = event.getCommand().split(" ")[0].toLowerCase();

            if (!allowedCommands.contains(command)) {
                event.setResult(CommandResult.denied());

                String msg = Messages.translate(MessageKey.AUTH_PROXY_COMMAND_BLOCKED, Messages.determineLocale(player));
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            }
        }
    }
}