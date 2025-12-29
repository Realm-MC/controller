package com.palacesky.controller.proxy.commands.cmds;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.motd.MotdService;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.*;

@Cmd(cmd = "motd", aliases = {}, onlyPlayer = false)
public class MotdCommand implements CommandInterface {

    private final MotdService motdService;
    private final Optional<SoundPlayer> soundPlayerOpt;

    private final String REQUIRED_PERMISSION = "controller.manager";
    private final String REQUIRED_GROUP = "Gerente";

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public MotdCommand() {
        this.motdService = ServiceRegistry.getInstance().requireService(MotdService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", REQUIRED_GROUP));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "ver":
                handleView(sender);
                break;
            case "definir":
                handleSetInteractive(sender, args);
                break;
            case "padrao":
                handleReset(sender);
                break;
            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(CommandSource sender) {
        String statusVal = motdService.isCustom() ? "&bPersonalizada" : "&bPadrão";

        sender.sendMessage(legacy.deserialize("&6Comandos disponíveis para Motd:"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(legacy.deserialize("&f• &6/motd ver &8– &7Exibe o MOTD atualmente configurado."));
        sender.sendMessage(legacy.deserialize("&f• &6/motd definir <mensagem> &8– &7Define uma nova segunda linha para o MOTD."));
        sender.sendMessage(legacy.deserialize("&f• &6/motd padrao &8– &7Restaura o MOTD para sua configuração padrão."));
        sender.sendMessage(Component.empty());
        sender.sendMessage(legacy.deserialize("&fStatus atual: " + statusVal));

        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleView(CommandSource sender) {
        String line1 = motdService.getCurrentLine1();
        String line2 = motdService.getCurrentLine2();

        sender.sendMessage(legacy.deserialize("&eMOTD atual do servidor:"));
        sender.sendMessage(legacy.deserialize("&f• Linha 1: " + line1));
        sender.sendMessage(legacy.deserialize("&f• Linha 2: " + line2));
        sender.sendMessage(legacy.deserialize("&eVisualização:"));
        sender.sendMessage(motdService.getMotdComponent());

        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleSetInteractive(CommandSource sender, String[] args) {
        if (args.length > 1) {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            motdService.setLine2(message);
            Messages.send(sender, MessageKey.MOTD_SET_SUCCESS);
            playSound(sender, SoundKeys.SUCCESS);
            return;
        }

        if (sender instanceof Player player) {
            motdService.addEditor(player.getUniqueId());

            player.sendMessage(legacy.deserialize("&aDigite a mensagem que deseja definir como MOTD do servidor."));
            player.sendMessage(legacy.deserialize("&7Caso deseje cancelar, digite \"cancelar\"."));

            Component clickable = Component.text("[Clique para digitar comando]", NamedTextColor.DARK_GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("Alternativa: Digite /motd definir <texto>", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.suggestCommand("/motd definir "));
            player.sendMessage(clickable);

            playSound(player, SoundKeys.NOTIFICATION);
        } else {
            Messages.send(sender, "&cConsole deve usar: /motd definir <mensagem>");
        }
    }

    private void handleReset(CommandSource sender) {
        motdService.resetToDefault();
        Messages.send(sender, MessageKey.MOTD_RESET_SUCCESS);
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player p) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(p, key));
        }
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("ver", "definir", "padrao");
        }
        return Collections.emptyList();
    }
}