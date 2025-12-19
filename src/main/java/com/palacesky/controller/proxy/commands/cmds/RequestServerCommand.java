package com.palacesky.controller.proxy.commands.cmds;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.server.ServerRegistryService;
import com.palacesky.controller.modules.server.data.ServerType;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Cmd(cmd = "request", aliases = {}, onlyPlayer = false)
public class RequestServerCommand implements CommandInterface {

    private final String REQUIRED_PERMISSION = "controller.manager";
    private final String REQUIRED_GROUP = "Gerente";
    private final ServerRegistryService serverRegistry;
    private final Optional<SoundPlayer> soundPlayerOpt;

    public RequestServerCommand() {
        this.serverRegistry = ServiceRegistry.getInstance().requireService(ServerRegistryService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", REQUIRED_GROUP));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length < 1) {
            Messages.send(sender, MessageKey.REQUEST_USAGE);
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String typeStr = args[0].toUpperCase();
        ServerType type;

        try {
            type = ServerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            String validTypes = Arrays.stream(ServerType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            Messages.send(sender, Message.of(MessageKey.REQUEST_INVALID_TYPE)
                    .with("type", typeStr)
                    .with("valid_types", validTypes));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        Messages.send(sender, Message.of(MessageKey.REQUEST_SUCCESS).with("type", type.name()));
        playSound(sender, SoundKeys.SUCCESS);

        serverRegistry.scaleUpNewServer(type);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound((Player) sender, key));
        }
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            String current = args[0].toUpperCase();
            return Arrays.stream(ServerType.values())
                    .map(Enum::name)
                    .filter(name -> name.startsWith(current))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}