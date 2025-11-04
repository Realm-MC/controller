package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Cmd(cmd = "hologram", aliases = {"holo", "holograms", "holograma", "hologramas"})
public class HologramCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final HologramService hologramService;

    public HologramCommand() {
        this.hologramService = ServiceRegistry.getInstance().getService(HologramService.class)
                .orElseThrow(() -> new IllegalStateException("HologramService not registered in ServiceRegistry!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender, label);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar": handleCreate(sender, args, label); break;
            case "remover": handleRemove(sender, args, label); break;
            case "list": handleList(sender); break;
            case "info": handleInfo(sender, args, label); break;
            case "tphere": handleTpHere(sender, args, label); break;
            case "toggleglow": handleToggleGlow(sender, args, label); break;
            case "addline": handleAddLine(sender, args, label); break;
            case "setline": handleSetLine(sender, args, label); break;
            case "removeline": handleRemoveLine(sender, args, label); break;
            case "reload":
                hologramService.reload();
                Messages.send(sender, MessageKey.DISPLAY_RELOADED);
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender, label);
                playSound(sender, SoundKeys.USAGE_ERROR);
                break;
        }
    }

    private void showHelp(CommandSender sender, String label) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Hologramas"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " criar <id> <texto>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_CREATE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " remover <id>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_REMOVE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " list")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_LIST)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " info <id>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_INFO)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " tphere <id>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_TPHERE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " toggleglow <id>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_TOGGLEGLOW)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " addline <id> <texto>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_ADDLINE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " setline <id> <linha> <texto>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_SETLINE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " removeline <id> <linha>")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_REMOVELINE)));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " reload")
                .with("description", Messages.translate(MessageKey.HOLOGRAM_HELP_RELOAD)));
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_REQUIRED);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSender sender, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            Optional<SoundPlayer> soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    private void handleCreate(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/" + label + " criar <id> <texto>");
            return;
        }
        String id = args[1];
        if (hologramService.getHologramEntry(id) != null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_ID).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        hologramService.showGlobal(id, player.getLocation(), List.of(text), false);

        Messages.send(sender, Message.of(MessageKey.DISPLAY_SPAWNED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, "/" + label + " remover <id>");
            return;
        }
        String id = args[1];
        if (hologramService.getHologramEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        hologramService.removeHologram(id);

        Messages.send(sender, Message.of(MessageKey.DISPLAY_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = hologramService.getAllHologramIds();
        if (ids.isEmpty()) {
            Messages.send(sender, MessageKey.DISPLAY_LIST_EMPTY);
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LIST_HEADER).with("ids", String.join("<gray>,<reset> ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, "/" + label + " info <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = hologramService.getHologramEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        String locationStr = String.format("%.2f, %.2f, %.2f em %s",
                entry.getX() != null ? entry.getX() : 0.0,
                entry.getY() != null ? entry.getY() : 0.0,
                entry.getZ() != null ? entry.getZ() : 0.0,
                entry.getWorld() != null ? entry.getWorld() : "N/A");
        String glow = Messages.translate(Boolean.TRUE.equals(entry.getGlow()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Holograma '" + id + "'"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "ID").with("value", entry.getId()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Localização Base").with("value", locationStr));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Brilho (Glow)").with("value", glow));

        List<String> lines = entry.getLines() != null ? entry.getLines() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Linhas de Texto").with("count", lines.size()));
        if (lines.isEmpty()) {
            Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", lines.get(i)));
            }
        }

        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, "/" + label + " tphere <id>");
            return;
        }
        String id = args[1];
        if (hologramService.getHologramEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        hologramService.teleportHologram(id, player.getLocation());

        Messages.send(sender, Message.of(MessageKey.DISPLAY_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.TELEPORT_WHOOSH);
    }

    private void handleToggleGlow(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, "/" + label + " toggleglow <id>");
            return;
        }
        String id = args[1];
        if (hologramService.getHologramEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        boolean newState = hologramService.toggleGlow(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_GLOW_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddLine(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            sendUsage(sender, "/" + label + " addline <id> <texto>");
            return;
        }
        String id = args[1];
        if (hologramService.getHologramEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        hologramService.addLine(id, text);

        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_ADDED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetLine(CommandSender sender, String[] args, String label) {
        if (args.length < 4) {
            sendUsage(sender, "/" + label + " setline <id> <linha> <texto>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        if (!hologramService.setLine(id, line, text)) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveLine(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            sendUsage(sender, "/" + label + " removeline <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        if (!hologramService.removeLine(id, line)) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        final List<String> completions = new ArrayList<>();
        final String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                    "criar", "remover", "list", "info", "tphere",
                    "toggleglow", "addline", "setline", "removeline", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("remover", "info", "tphere", "toggleglow", "addline", "setline", "removeline").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, hologramService.getAllHologramIds(), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}