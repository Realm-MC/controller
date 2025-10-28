package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry; // Usado para info
import com.realmmc.controller.spigot.entities.npcs.NPCService;
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
    // <<< CORREÇÃO: Nome do grupo associado à permissão >>>
    private final String requiredGroupName = "Gerente"; // Ou o nome de display correto do grupo Manager
    private final NPCService npcService;

    public NpcCommand() {
        this.npcService = ServiceRegistry.getInstance().getService(NPCService.class)
                .orElseThrow(() -> new IllegalStateException("NPCService não registrado no ServiceRegistry!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            // <<< CORREÇÃO: Usar COMMON_NO_PERMISSION_GROUP >>>
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
            // <<< FIM CORREÇÃO >>>
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender);
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
            case "togglename": handleToggleName(sender, args); break;
            case "togglelook": handleToggleLook(sender, args); break;
            case "addline": handleAddLine(sender, args); break;
            case "setline": handleSetLine(sender, args); break;
            case "removeline": handleRemoveLine(sender, args); break;
            case "addaction": handleAddAction(sender, args); break;
            case "removeaction": handleRemoveAction(sender, args); break;
            case "listactions": handleListActions(sender, args); break;
            default:
                showHelp(sender);
                playSound(sender, SoundKeys.USAGE_ERROR);
                break;
        }
    }

    // --- Métodos de Ajuda e Utilitários ---

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

    // --- Handlers dos Subcomandos ---

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/npc criar <id> <skin> [nome]"); return; }
        String id = args[1];
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
        String originalId = args[1];
        String newId = args[2];
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
        String id = args[1];
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
        String id = args[1];
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
        String id = args[1];
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String locationStr = String.format("%.2f, %.2f, %.2f em %s",
                entry.getX() != null ? entry.getX() : 0.0,
                entry.getY() != null ? entry.getY() : 0.0,
                entry.getZ() != null ? entry.getZ() : 0.0,
                entry.getWorld() != null ? entry.getWorld() : "N/A");
        String lookAtPlayer = Messages.translate(Boolean.TRUE.equals(entry.getIsMovible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        String nameVisible = Messages.translate(Boolean.TRUE.equals(entry.getHologramVisible()) ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "NPC '" + id + "'"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.NPC_INFO_SKIN)).with("value", entry.getItem() != null ? entry.getItem() : "Padrão"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.NPC_INFO_NAME)).with("value", entry.getMessage() != null ? entry.getMessage() : "---"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.NPC_INFO_LOCATION)).with("value", locationStr));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.NPC_INFO_LOOKATPLAYER)).with("value", lookAtPlayer));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.NPC_INFO_NAMEVISIBLE)).with("value", nameVisible));

        List<String> lines = entry.getLines() != null ? entry.getLines() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.NPC_INFO_LINES)).with("count", lines.size()));
        if (lines.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < lines.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", lines.get(i))); } }

        List<String> actions = entry.getActions() != null ? entry.getActions() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.NPC_INFO_ACTIONS)).with("count", actions.size()));
        if (actions.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else { for (int i = 0; i < actions.size(); i++) { Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", i + 1).with("value", actions.get(i))); } }

        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/npc tphere <id>"); return; }
        String id = args[1];
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
        String id = args[1];
        String skin = args[2];
        if (npcService.getNpcEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        npcService.updateNpcSkin(id, skin);
        Messages.send(sender, Message.of(MessageKey.NPC_SKIN_SET).with("id", id).with("skin", skin));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleToggleName(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc togglename <id>"); return; }
        String id = args[1];
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
        String id = args[1];
        DisplayEntry entry = npcService.getNpcEntry(id);
        if (entry == null) {
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
        String id = args[1];
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
        String id = args[1]; int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!npcService.setLine(id, line, text)) {
            DisplayEntry entry = npcService.getNpcEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_SET).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc removeline <id> <linha>"); return; }
        String id = args[1]; int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!npcService.removeLine(id, line)) {
            DisplayEntry entry = npcService.getNpcEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_LINE_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/npc addaction <id> <ação>"); return; }
        String id = args[1];
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
        String id = args[1]; int line;
        try {
            line = Integer.parseInt(args[2]);
            if (line <= 0) throw new NumberFormatException("Action line number must be positive");
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (!npcService.removeAction(id, line)) {
            DisplayEntry entry = npcService.getNpcEntry(id);
            if (entry == null) {
                Messages.send(sender, Message.of(MessageKey.NPC_NOT_FOUND).with("id", id));
            } else {
                Messages.send(sender, MessageKey.DISPLAY_INVALID_ACTION_LINE);
            }
            playSound(sender, SoundKeys.ERROR); return;
        }
        Messages.send(sender, Message.of(MessageKey.NPC_ACTION_REMOVED).with("id", id).with("line", line));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/npc listactions <id>"); return; }
        String id = args[1];
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

    // --- Tab Completion ---

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) { return Collections.emptyList(); }
        final List<String> completions = new ArrayList<>();
        final String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                    "criar", "clone", "remover", "list", "info", "tphere", "setskin", "rename",
                    "togglename", "togglelook", "addline", "setline", "removeline",
                    "addaction", "removeaction", "listactions", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("clone", "remover", "info", "tphere", "setskin", "rename",
                    "togglename", "togglelook", "addline", "setline", "removeline",
                    "addaction", "removeaction", "listactions").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, npcService.getAllNpcIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar") || sub.equals("setskin")) {
                List<String> suggestions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                suggestions.add("https://");
                suggestions.add("default");
                suggestions.add("player");
                StringUtil.copyPartialMatches(currentArg, suggestions, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}