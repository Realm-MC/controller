package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
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
    private final DisplayItemService displayService;

    public DisplayCommand() {
        this.displayService = Main.getInstance().getDisplayItemService();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", "Gerente"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help": showHelp(sender); break;
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
            default: showHelp(sender); break;
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Display Items"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display criar <id> <material>").with("description", "Cria um novo item flutuante."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display clone <id original> <novo id>").with("description", "Duplica um item flutuante."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display remover <id>").with("description", "Remove um item flutuante."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display list").with("description", "Lista todos os itens flutuantes."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display info <id>").with("description", "Mostra informações de um item."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display tphere <id>").with("description", "Teleporta um item para a sua localização."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display setitem <id> <material>").with("description", "Altera o item exibido."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display setscale <id> <escala>").with("description", "Altera o tamanho do item (ex: 1.5)."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display setbillboard <id> <tipo>").with("description", "Altera como o item encara o jogador."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display toggleglow <id>").with("description", "Ativa/desativa o brilho do item."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display togglelines <id>").with("description", "Mostra/esconde as linhas de texto."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display addline <id> <texto>").with("description", "Adiciona uma linha de texto."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display setline <id> <linha> <texto>").with("description", "Modifica uma linha de texto."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display removeline <id> <linha>").with("description", "Remove uma linha de texto."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display addaction <id> <ação>").with("description", "Adiciona uma ação de clique."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display removeaction <id> <linha>").with("description", "Remove uma ação de clique."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display listactions <id>").with("description", "Lista as ações de um item."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/display reload").with("description", "Recarrega todos os Display Items do ficheiro."));
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
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); return; }
        if (args.length < 3) { sendUsage(sender, "/display criar <id> <material>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) != null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_ID).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Material material;
        try { material = Material.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2])); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.createDisplay(id, player.getLocation(), material);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_SPAWNED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); return; }
        if (args.length < 3) { sendUsage(sender, "/display clone <id original> <novo id>"); return; }
        String originalId = args[1];
        String newId = args[2];
        if (displayService.getDisplayEntry(originalId) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", originalId)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        if (displayService.getDisplayEntry(newId) != null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_ID).with("id", newId)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.cloneDisplay(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display remover <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.removeDisplay(id);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = displayService.getAllDisplayIds();
        if (ids.isEmpty()) { Messages.send(sender, MessageKey.DISPLAY_LIST_EMPTY); playSound(sender, SoundKeys.NOTIFICATION); return; }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LIST_HEADER).with("ids", String.join(", ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display info <id>"); return; }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        String locationStr = String.format("%.2f, %.2f, %.2f em %s", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld());
        String glow = Messages.translate(Boolean.TRUE.equals(entry.getGlow()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        String linesVisible = Messages.translate(Boolean.TRUE.equals(entry.getHologramVisible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Display Item " + id));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "ID").with("value", entry.getId()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Item").with("value", entry.getItem()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Localização").with("value", locationStr));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Escala").with("value", entry.getScale()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Billboard").with("value", entry.getBillboard()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Brilho (Glow)").with("value", glow));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Visibilidade das Linhas").with("value", linesVisible));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Linhas de Texto").with("count", entry.getLines().size()));
        if (entry.getLines().isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < entry.getLines().size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", entry.getLines().get(i))); } }
        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); return; }
        if (args.length < 2) { sendUsage(sender, "/display tphere <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.teleportDisplay(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetItem(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setitem <id> <material>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Material material;
        try { material = Material.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_MATERIAL).with("material", args[2])); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.setItem(id, material);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ITEM_SET).with("id", id).with("material", material.name()));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetScale(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setscale <id> <escala>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        float scale;
        try { scale = Float.parseFloat(args[2]); }
        catch (NumberFormatException e) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_SCALE).with("scale", args[2])); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.setScale(id, scale);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_SCALE_SET).with("id", id).with("scale", scale));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetBillboard(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display setbillboard <id> <tipo>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Display.Billboard billboard;
        try { billboard = Display.Billboard.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { Messages.send(sender, Message.of(MessageKey.DISPLAY_INVALID_BILLBOARD).with("type", args[2])); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.setBillboard(id, billboard.name());
        Messages.send(sender, Message.of(MessageKey.DISPLAY_BILLBOARD_SET).with("id", id).with("billboard", billboard.name()));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleGlow(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display toggleglow <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        boolean newState = displayService.toggleGlow(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_GLOW_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleLines(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display togglelines <id>"); return; }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        boolean newState = displayService.toggleLinesVisibility(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINES_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display addline <id> <texto>"); return; }
        String id = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.addLine(id, text);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_ADDED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) { sendUsage(sender, "/display setline <id> <linha> <texto>"); return; }
        String id = args[1]; int line;
        try { line = Integer.parseInt(args[2]); } catch (NumberFormatException e) { Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!displayService.setLine(id, line, text)) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display removeline <id> <linha>"); return; }
        String id = args[1]; int line;
        try { line = Integer.parseInt(args[2]); } catch (NumberFormatException e) { Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        if (!displayService.removeLine(id, line)) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display addaction <id> <ação>"); return; }
        String id = args[1];
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (displayService.getDisplayEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        displayService.addAction(id, action);
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ACTION_ADDED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/display removeaction <id> <linha>"); return; }
        String id = args[1]; int line;
        try { line = Integer.parseInt(args[2]); } catch (NumberFormatException e) { Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        if (!displayService.removeAction(id, line)) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        Messages.send(sender, Message.of(MessageKey.DISPLAY_ACTION_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/display listactions <id>"); return; }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.DISPLAY_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.USAGE_ERROR); return; }
        List<String> actions = entry.getActions();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Ações de clique para o Display " + id).with("count", actions == null ? 0 : actions.size()));
        if (actions == null || actions.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < actions.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", actions.get(i))); } }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) { return Collections.emptyList(); }
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("criar", "clone", "remover", "list", "info", "tphere", "setitem", "setscale", "setbillboard", "toggleglow", "togglelines", "addline", "setline", "removeline", "addaction", "removeaction", "listactions", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!sub.equals("criar") && !sub.equals("list") && !sub.equals("reload") && !sub.equals("help")) {
                StringUtil.copyPartialMatches(args[1], displayService.getAllDisplayIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar") || sub.equals("setitem")) {
                List<String> materials = Stream.of(Material.values()).filter(Material::isItem).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], materials, completions);
            } else if (sub.equals("setbillboard")) {
                List<String> billboards = Stream.of(Display.Billboard.values()).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], billboards, completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}