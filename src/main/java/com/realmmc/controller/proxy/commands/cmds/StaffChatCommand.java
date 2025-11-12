package com.realmmc.controller.proxy.commands.cmds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Cmd(cmd = "s", aliases = {"sc"}, onlyPlayer = true)
public class StaffChatCommand implements CommandInterface {

    private final String requiredPermission = "controller.helper";
    private final String requiredGroupName = "Ajudante";
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final Logger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StaffChatCommand() {
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.logger = Logger.getLogger(StaffChatCommand.class.getName());
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(requiredPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(sender, label, "<mensagem>");
            return;
        }

        String message = String.join(" ", args);
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        String serverName = player.getCurrentServer()
                .map(ServerConnection::getServerInfo)
                .map(ServerInfo::getName)
                .orElse("Unknown");

        String formattedName = NicknameFormatter.getFullFormattedNick(uuid);

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("serverName", serverName);
            payload.put("playerName", playerName);
            payload.put("formattedName", formattedName);
            payload.put("message", message);

            RedisPublisher.publish(RedisChannel.STAFF_CHAT, payload.toString());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao publicar mensagem do StaffChat no Redis", e);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            playSound(sender, SoundKeys.ERROR);
        }
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        return Collections.emptyList();
    }
}