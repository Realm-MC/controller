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
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Cmd(cmd = "npc", aliases = {})
public class NpcCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final NPCService npcService;

    public NpcCommand() {
        this.npcService = Main.getInstance().getNpcService();
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
            case "clone": handleClone(sender, args); break;
            case "remover": handleRemove(sender, args); break;
            case "rename": handleRename(sender, args); break;
            case "info": handleInfo(sender, args); break;
            case "list": handleList(sender); break;
            case "tphere": handleTpHere(sender, args); break;
            case "setskin": handleSetSkin(sender, args); break;
            case "togglename": handleToggleName(sender, args); break;
            case "togglelook": handleToggleLook(sender, args); break;
            case "addline": handleAddLine(sender, args); break;
            case "setline": handleSetLine(sender, args); break;
            case "removeline": handleRemoveLine(sender, args); break;
            case "addaction": handleAddAction(sender, args); break;
            case "removeaction": handleRemoveAction(sender, args); break;
            case "listactions": handleListActions(sender, args); break;
            default: showHelp(sender); break;
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "NPCs"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc criar <id> <skin> [nome]").with("description", "Cria um novo NPC."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc clone <id original> <novo id>").with("description", "Duplica um NPC na sua posição."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc remover <id>").with("description", "Remove um NPC permanentemente."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc rename <id> <novo nome>").with("description", "Renomeia um NPC."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc list").with("description", "Lista todos os NPCs existentes."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc info <id>").with("description", "Mostra informações detalhadas de um NPC."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc tphere <id>").with("description", "Teleporta um NPC para a sua localização."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc setskin <id> <skin>").with("description", "Altera a skin de um NPC (nome do jogador ou URL)."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc togglename <id>").with("description", "Mostra/esconde o nome de um NPC."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc togglelook <id>").with("description", "Ativa/desativa o NPC olhar para os jogadores."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc addline <id> <texto>").with("description", "Adiciona uma linha de texto sobre o NPC."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc setline <id> <linha> <texto>").with("description", "Modifica uma linha de texto existente."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc removeline <id> <linha>").with("description", "Remove uma linha de texto."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc addaction <id> <ação>").with("description", "Adiciona uma ação de clique."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc removeaction <id> <linha>").with("description", "Remove uma ação de clique."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/npc listactions <id>").with("description", "Lista as ações de um NPC."));
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
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
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/npc criar <id> <skin> [nome]");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) != null) {
            Messages.send(sender, Message.of(MessageKey.NPC_INVALID_ID).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String skin = args[2];
        String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        npcService.createNpc(id, player.getLocation(), name, skin);
        Messages.send(sender, Message.of(MessageKey.NPC_CREATED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/npc clone <id original> <novo id>");
            return;
        }
        String originalId = args[1];
        String newId = args[2];
        if (npcService.getNpcEntry(originalId) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", originalId));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (npcService.getNpcEntry(newId) != null) {
            Messages.send(sender, Message.of(MessageKey.NPC_INVALID_ID).with("id", newId));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.cloneNpc(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.NPC_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc remover <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.removeNpc(id);
        Messages.send(sender, Message.of(MessageKey.NPC_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc rename <id> <novo nome>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcService.renameNpc(id, newName);
        Messages.send(sender, Message.of(MessageKey.NPC_RENAMED).with("id", id).with("name", newName));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = npcService.getAllNpcIds();
        if (ids.isEmpty()) {
            Messages.send(sender, MessageKey.NPC_LIST_EMPTY);
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LIST_HEADER).with("ids", String.join(", ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc info <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String locationStr = String.format("%.2f, %.2f, %.2f em %s", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld());
        String lookAtPlayer = Messages.translate(Boolean.TRUE.equals(entry.getIsMovible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        String nameVisible = Messages.translate(Boolean.TRUE.equals(entry.getHologramVisible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "NPC " + id));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "ID").with("value", entry.getId()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Skin").with("value", entry.getItem()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Nome").with("value", entry.getMessage()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Localização").with("value", locationStr));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Olhar para Jogador").with("value", lookAtPlayer));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Visibilidade do Nome").with("value", nameVisible));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Linhas de Texto").with("count", entry.getLines().size()));
        if (entry.getLines().isEmpty()) {
            Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
        } else {
            for (int i = 0; i < entry.getLines().size(); i++) {
                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", entry.getLines().get(i)));
            }
        }
        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, "/npc tphere <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.teleportNpc(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.NPC_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc setskin <id> <skin>");
            return;
        }
        String id = args[1];
        String skin = args[2];
        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.updateNpcSkin(id, skin);
        Messages.send(sender, Message.of(MessageKey.NPC_SKIN_SET).with("id", id).with("skin", skin));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleName(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc togglename <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = npcService.toggleNameVisibility(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.NPC_NAME_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleLook(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc togglelook <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = npcService.toggleLookAtPlayer(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.NPC_LOOK_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc addline <id> <texto>");
            return;
        }
        String id = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.addLine(id, text);
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_ADDED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/npc setline <id> <linha> <texto>");
            return;
        }
        String id = args[1];
        int line;
        try { line = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!npcService.setLine(id, line, text)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc removeline <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try { line = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        if (!npcService.removeLine(id, line)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc addaction <id> <ação>");
            return;
        }
        String id = args[1];
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.addAction(id, action);
        Messages.send(sender, Message.of(MessageKey.NPC_ACTION_ADDED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc removeaction <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try { line = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        if (!npcService.removeAction(id, line)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_ACTION_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc listactions <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        List<String> actions = entry.getActions();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Ações de clique para o NPC " + id).with("count", actions == null ? 0 : actions.size()));
        if (actions == null || actions.isEmpty()) {
            Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
        } else {
            for (int i = 0; i < actions.size(); i++) {
                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", actions.get(i)));
            }
        }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("criar", "clone", "remover", "list", "info", "tphere", "setskin", "rename", "togglename", "togglelook", "addline", "setline", "removeline", "addaction", "removeaction", "listactions", "help");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            List<String> npcIds = new ArrayList<>(npcService.getAllNpcIds());
            switch(sub) {
                case "remover":
                case "clone":
                case "info":
                case "tphere":
                case "setskin":
                case "rename":
                case "togglename":
                case "togglelook":
                case "addline":
                case "setline":
                case "removeline":
                case "addaction":
                case "removeaction":
                case "listactions":
                    StringUtil.copyPartialMatches(args[1], npcIds, completions);
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setskin")) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            players.add("https://");
            StringUtil.copyPartialMatches(args[2], players, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}