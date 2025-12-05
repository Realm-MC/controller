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
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "npc", aliases = {})
public class NpcCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final NPCService npcService;

    public NpcCommand() {
        this.npcService = ServiceRegistry.getInstance().getService(NPCService.class)
                .orElseThrow(() -> new IllegalStateException("NPCService não registrado!"));
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
            case "criar": handleCreate(sender, args); break;
            case "clone": handleClone(sender, args); break;
            case "remover": handleRemove(sender, args); break;
            case "rename": handleRename(sender, args); break;
            case "info": handleInfo(sender, args); break;
            case "list": handleList(sender); break;
            case "tphere": handleTpHere(sender, args); break;
            case "setskin": handleSetSkin(sender, args); break;
            case "settype": handleSetType(sender, args); break;
            case "togglename": handleToggleName(sender, args); break;
            case "togglelook": handleToggleLook(sender, args); break;
            case "addline": handleAddLine(sender, args); break;
            case "setline": handleSetLine(sender, args); break;
            case "removeline": handleRemoveLine(sender, args); break;
            case "addaction": handleAddAction(sender, args); break;
            case "removeaction": handleRemoveAction(sender, args); break;
            case "listactions": handleListActions(sender, args); break;
            case "reload":
                npcService.reloadAll();
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
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "NPCs"));
        sendHelpLine(sender, label, "criar <id> <skin> [nome]", "Cria um novo NPC.");
        sendHelpLine(sender, label, "remover <id>", "Remove um NPC.");
        sendHelpLine(sender, label, "list", "Lista todos os NPCs.");
        sendHelpLine(sender, label, "info <id>", "Mostra informações do NPC.");
        sendHelpLine(sender, label, "tphere <id>", "Teleporta o NPC até você.");
        sendHelpLine(sender, label, "clone <id> <novo_id>", "Clona um NPC.");
        sendHelpLine(sender, label, "rename <id> <nome>", "Altera o nome do NPC.");
        sendHelpLine(sender, label, "setskin <id> <skin>", "Altera a skin (nick, url ou 'default').");
        sendHelpLine(sender, label, "settype <id> <tipo>", "Altera o tipo de entidade (PLAYER, ZOMBIE, etc).");
        sendHelpLine(sender, label, "togglename <id>", "Mostra/Esconde o nome.");
        sendHelpLine(sender, label, "togglelook <id>", "Ativa/Desativa olhar para o jogador.");
        sendHelpLine(sender, label, "addline <id> <texto>", "Adiciona uma linha de holograma.");
        sendHelpLine(sender, label, "setline <id> <linha> <texto>", "Edita uma linha.");
        sendHelpLine(sender, label, "removeline <id> <linha>", "Remove uma linha.");
        sendHelpLine(sender, label, "addaction <id> <ação>", "Adiciona uma ação ao clicar.");
        sendHelpLine(sender, label, "removeaction <id> <index>", "Remove uma ação.");
        sendHelpLine(sender, label, "listactions <id>", "Lista as ações.");
        sendHelpLine(sender, label, "reload", "Recarrega os NPCs do disco.");

        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendHelpLine(CommandSender sender, String label, String args, String description) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE)
                .with("usage", "/" + label + " " + args)
                .with("description", description));
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
        if (args.length < 3) { sendUsage(sender, "/npc criar <id> <skin> [nome]"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) != null) {
            Messages.send(sender, Message.of(MessageKey.NPC_INVALID_ID).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String skin = args[2];
        String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        npcService.createNpc(id, player.getLocation(), name, skin);
        Messages.send(sender, Message.of(MessageKey.NPC_CREATED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/npc clone <id original> <novo id>"); return; }
        String originalId = args[1].toLowerCase();
        String newId = args[2].toLowerCase();
        if (npcService.getNpcEntry(originalId) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", originalId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (npcService.getNpcEntry(newId) != null) {
            Messages.send(sender, Message.of(MessageKey.NPC_INVALID_ID).with("id", newId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        npcService.cloneNpc(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.NPC_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc remover <id>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        npcService.removeNpc(id);
        Messages.send(sender, Message.of(MessageKey.NPC_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc rename <id> <novo nome>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcService.renameNpc(id, newName);
        Messages.send(sender, Message.of(MessageKey.NPC_RENAMED).with("id", id).with("name", newName));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleList(CommandSender sender) {
        var ids = npcService.getAllNpcIds();
        if (ids.isEmpty()) {
            Messages.send(sender, MessageKey.NPC_LIST_EMPTY);
            playSound(sender, SoundKeys.NOTIFICATION); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LIST_HEADER).with("ids", String.join("<gray>,<reset> ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc info <id>"); return; }
        String id = args[1].toLowerCase();
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "NPC '" + id + "'"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Tipo").with("value", entry.getEntityType() != null ? entry.getEntityType() : "PLAYER"));
        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/npc tphere <id>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        npcService.teleportNpc(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.NPC_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.TELEPORT_WHOOSH);
    }

    private void handleSetSkin(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc setskin <id> <skin>"); return; }
        String id = args[1].toLowerCase();
        String skin = args[2];
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        npcService.updateNpcSkin(id, skin);
        Messages.send(sender, Message.of(MessageKey.NPC_SKIN_SET).with("id", id).with("skin", skin));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetType(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc settype <id> <tipo>"); return; }
        String id = args[1].toLowerCase();
        String type = args[2].toUpperCase();

        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }

        try {
            com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.getByName(type.toLowerCase());
        } catch (Exception e) {
        }

        npcService.setEntityType(id, type);
        Messages.send(sender, Message.of(MessageKey.SCONFIG_PROP_UPDATED).with("prop", "Tipo").with("id", id).with("value", type));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleToggleName(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc togglename <id>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        boolean newState = npcService.toggleNameVisibility(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.NPC_NAME_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleToggleLook(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc togglelook <id>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        boolean newState = npcService.toggleLookAtPlayer(id);
        String status = Messages.translate(newState ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.NPC_LOOK_TOGGLED).with("id", id).with("status", status));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc addline <id> <texto>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcService.addLine(id, text);
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_ADDED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) { sendUsage(sender, "/npc setline <id> <linha> <texto>"); return; }
        String id = args[1].toLowerCase(); int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!npcService.setLine(id, line, text)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc removeline <id> <linha>"); return; }
        String id = args[1].toLowerCase(); int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!npcService.removeLine(id, line)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc addaction <id> <ação>"); return; }
        String id = args[1].toLowerCase();
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcService.addAction(id, action);
        Messages.send(sender, Message.of(MessageKey.NPC_ACTION_ADDED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc removeaction <id> <linha>"); return; }
        String id = args[1].toLowerCase(); int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Action line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!npcService.removeAction(id, line)) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_ACTION_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc listactions <id>"); return; }
        String id = args[1].toLowerCase();
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        List<String> actions = entry.getActions() != null ? entry.getActions() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.NPC_INFO_ACTIONS) + " para '" + id + "'").with("count", actions.size()));
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
                    "criar", "clone", "remover", "list", "info", "tphere", "setskin", "settype", "rename",
                    "togglename", "togglelook", "addline", "setline", "removeline",
                    "addaction", "removeaction", "listactions", "help", "reload"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("clone", "remover", "info", "tphere", "setskin", "settype", "rename",
                    "togglename", "togglelook", "addline", "setline", "removeline",
                    "addaction", "removeaction", "listactions").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, npcService.getAllNpcIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar") || sub.equals("setskin")) {
                StringUtil.copyPartialMatches(currentArg, Arrays.asList("player", "default", "http://"), completions);
            } else if (sub.equals("settype")) {
                List<String> mobTypes = Stream.of(EntityType.values())
                        .filter(et -> et.isAlive() && et.isSpawnable())
                        .map(Enum::name)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, mobTypes, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}