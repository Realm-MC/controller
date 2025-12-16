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
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "display", aliases = {"displays"})
public class DisplayCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final DisplayItemService displayService;

    public DisplayCommand() {
        this.displayService = ServiceRegistry.getInstance().getService(DisplayItemService.class)
                .orElseThrow(() -> new IllegalStateException("DisplayItemService not registered in ServiceRegistry!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar": handleCreate(sender, args); break;
            case "remover": handleRemove(sender, args); break;
            case "clone": handleClone(sender, args); break;
            case "list": handleList(sender); break;
            case "info": handleInfo(sender, args); break;
            case "tphere": handleTpHere(sender, args); break;
            case "setitem": handleSetItem(sender, args); break;
            case "setscale": handleSetScale(sender, args); break;
            case "setbillboard": handleSetBillboard(sender, args); break;
            case "toggleglow": handleToggleGlow(sender, args); break;
            case "togglelines": handleToggleLines(sender, args); break;
            case "addline": handleAddLine(sender, args); break;
            case "setline": handleSetLine(sender, args); break;
            case "removeline": handleRemoveLine(sender, args); break;
            case "addaction": handleAddAction(sender, args); break;
            case "removeaction": handleRemoveAction(sender, args); break;
            case "listactions": handleListActions(sender, args); break;
            case "reload":
                displayService.reload();
                Messages.send(sender, MessageKey.DISPLAY_RELOADED);
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender);
                playSound(sender, SoundKeys.USAGE_ERROR);
                break;
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Display Items"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display criar <id> <material>").with("description", "Cria um novo item flutuante."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display list").with("description", "Lista itens."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display info <id>").with("description", "Info do item."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display tphere <id>").with("description", "Teleporta para ti."));
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

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/display criar <id> <material>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) != null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_ID).with("id", id));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
            if (!material.isItem()) {
                Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2]));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
        } catch (IllegalArgumentException e) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2]));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        displayService.createDisplay(id, player.getLocation(), material);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_SPAWNED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/display clone <id original> <novo id>"); return; }
        String originalId = args[1];
        String newId = args[2];
        if (displayService.getDisplayEntry(originalId) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", originalId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (displayService.getDisplayEntry(newId) != null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_ID).with("id", newId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.cloneDisplay(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display remover <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.removeDisplay(id);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = displayService.getAllDisplayIds();
        if (ids.isEmpty()) {
            Messages.send(sender, MessageKey.DISPLAY_LIST_EMPTY);
            playSound(sender, SoundKeys.NOTIFICATION); return;
        }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LIST_HEADER).with("ids", String.join("<gray>,<reset> ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display info <id>"); return; }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        java.util.Locale locale = Messages.determineLocale(sender);

        String locationStr = String.format("%.2f, %.2f, %.2f em %s",
                entry.getX() != null ? entry.getX() : 0.0,
                entry.getY() != null ? entry.getY() : 0.0,
                entry.getZ() != null ? entry.getZ() : 0.0,
                entry.getWorld() != null ? entry.getWorld() : "N/A");
        String glow = Messages.translate(Boolean.TRUE.equals(entry.getGlow()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE, locale);
        String linesVisible = Messages.translate(Boolean.TRUE.equals(entry.getHologramVisible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE, locale);

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Display Item '" + id + "'"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_ID, locale)).with("value", entry.getId()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_ITEM, locale)).with("value", entry.getItem() != null ? entry.getItem() : "N/A"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_LOCATION, locale)).with("value", locationStr));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_SCALE, locale)).with("value", entry.getScale() != null ? entry.getScale() : "N/A"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_BILLBOARD, locale)).with("value", entry.getBillboard() != null ? entry.getBillboard() : "N/A"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_GLOW, locale)).with("value", glow));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.LABEL_LINES_VISIBLE, locale)).with("value", linesVisible));

        List<String> lines = entry.getLines() != null ? entry.getLines() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.LABEL_TEXT_LINES, locale)).with("count", lines.size()));
        if (lines.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < lines.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", lines.get(i))); } }

        List<String> actions = entry.getActions() != null ? entry.getActions() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.LABEL_CLICK_ACTIONS, locale)).with("count", actions.size()));
        if (actions.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < actions.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", actions.get(i))); } }

        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/display tphere <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.teleportDisplay(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.TELEPORT_WHOOSH);
    }

    private void handleSetItem(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setitem <id> <material>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
            if (!material.isItem()) {
                Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2]));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
        } catch (IllegalArgumentException e) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2]));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.setItem(id, material);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ITEM_SET).with("id", id).with("material", material.name()));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetScale(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setscale <id> <escala>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        float scale;
        try {
            scale = Float.parseFloat(args[2]);
            if (scale <= 0) throw new NumberFormatException("Scale must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_SCALE).with("scale", args[2]));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.setScale(id, scale);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_SCALE_SET).with("id", id).with("scale", String.format("%.2f", scale)));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetBillboard(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setbillboard <id> <tipo>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Display.Billboard billboard;
        try {
            billboard = Display.Billboard.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_BILLBOARD).with("type", args[2]));
            playSound(sender, SoundKeys.ERROR); return;
        }
        displayService.setBillboard(id, billboard.name());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_BILLBOARD_SET).with("id", id).with("billboard", billboard.name()));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleToggleGlow(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display toggleglow <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        boolean newState = displayService.toggleGlow(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_GLOW_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleToggleLines(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display togglelines <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        boolean newState = displayService.toggleLinesVisibility(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINES_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display addline <id> <texto>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        displayService.addLine(id, text);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_ADDED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) { sendUsage(sender, "/display setline <id> <linha> <texto>"); return; }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!displayService.setLine(id, line, text)) {
            DisplayEntry entry = displayService.getDisplayEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display removeline <id> <linha>"); return; }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!displayService.removeLine(id, line)) {
            DisplayEntry entry = displayService.getDisplayEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display addaction <id> <ação>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        displayService.addAction(id, action);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ACTION_ADDED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display removeaction <id> <linha>"); return; }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Action line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!displayService.removeAction(id, line)) {
            DisplayEntry entry = displayService.getDisplayEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ACTION_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display listactions <id>"); return; }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        List<String> actions = entry.getActions() != null ? entry.getActions() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.LABEL_CLICK_ACTIONS, Messages.determineLocale(sender))).with("count", actions.size()));
        if (actions.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < actions.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", actions.get(i))); } }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) { return Collections.emptyList(); }
        final List<String> completions = new ArrayList<>();
        final String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                    "criar", "clone", "remover", "list", "info", "tphere", "setitem", "setscale",
                    "setbillboard", "toggleglow", "togglelines", "addline", "setline", "removeline",
                    "addaction", "removeaction", "listactions", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("clone", "remover", "info", "tphere", "setitem", "setscale",
                    "setbillboard", "toggleglow", "togglelines", "addline", "setline",
                    "removeline", "addaction", "removeaction", "listactions").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, displayService.getAllDisplayIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar") || sub.equals("setitem")) {
                List<String> materials = Stream.of(Material.values())
                        .filter(Material::isItem)
                        .map(m -> m.name().toLowerCase())
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, materials, completions);
            } else if (sub.equals("setbillboard")) {
                List<String> billboards = Stream.of(Display.Billboard.values())
                        .map(b -> b.name().toLowerCase())
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, billboards, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}