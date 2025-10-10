package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Cmd(cmd = "npc", aliases = {})
public class NpcCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final NPCService npcService;
    private final SoundService soundService;

    public NpcCommand() {
        this.npcService = Main.getInstance().getNpcService();
        this.soundService = ServiceRegistry.getInstance().getService(SoundService.class)
                .orElseThrow(() -> new IllegalStateException("SoundService não foi encontrado!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, "<red>Apenas o grupo Gerente ou superior pode executar este comando.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
                showHelp(sender);
                break;
            case "criar":
                handleCreate(sender, args);
                break;
            case "clone":
                handleClone(sender, args);
                break;
            case "remover":
                handleRemove(sender, args);
                break;
            case "rename":
                handleRename(sender, args);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "tphere":
                handleTpHere(sender, args);
                break;
            case "setskin":
                handleSetSkin(sender, args);
                break;
            case "togglename":
                handleToggleName(sender, args);
                break;
            case "togglelook":
                handleToggleLook(sender, args);
                break;
            case "addline":
                handleAddLine(sender, args);
                break;
            case "setline":
                handleSetLine(sender, args);
                break;
            case "removeline":
                handleRemoveLine(sender, args);
                break;
            case "addaction":
                handleAddAction(sender, args);
                break;
            case "removeaction":
                handleRemoveAction(sender, args);
                break;
            case "listactions":
                handleListActions(sender, args);
                break;
            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(" ");
        sender.sendMessage("§6Comandos disponíveis para NPCs");
        sender.sendMessage("§e /npc criar <id> <skin> [nome] §8- §7Cria um novo NPC.");
        sender.sendMessage("§e /npc clone <id original> <novo id> §8- §7Duplica um NPC na sua posição.");
        sender.sendMessage("§e /npc remover <id> §8- §7Remove um NPC permanentemente.");
        sender.sendMessage("§e /npc rename <id> <novo nome> §8- §7Renomeia um NPC.");
        sender.sendMessage("§e /npc list §8- §7Lista todos os NPCs existentes.");
        sender.sendMessage("§e /npc info <id> §8- §7Mostra informações detalhadas de um NPC.");
        sender.sendMessage("§e /npc tphere <id> §8- §7Teleporta um NPC para a sua localização.");
        sender.sendMessage("§e /npc setskin <id> <skin> §8- §7Altera a skin de um NPC (nome do jogador ou URL).");
        sender.sendMessage("§e /npc togglename <id> §8- §7Mostra/esconde o nome de um NPC.");
        sender.sendMessage("§e /npc togglelook <id> §8- §7Ativa/desativa o NPC olhar para os jogadores.");
        sender.sendMessage("§e /npc addline <id> <texto> §8- §7Adiciona uma linha de texto sobre o NPC.");
        sender.sendMessage("§e /npc setline <id> <linha> <texto> §8- §7Modifica uma linha de texto existente.");
        sender.sendMessage("§e /npc removeline <id> <linha> §8- §7Remove uma linha de texto.");
        sender.sendMessage("§e /npc addaction <id> <ação> §8- §7Adiciona uma ação de clique.");
        sender.sendMessage("§e /npc removeaction <id> <linha> §8- §7Remove uma ação de clique.");
        sender.sendMessage("§e /npc listactions <id> §8- §7Lista as ações de um NPC.");
        sender.sendMessage(" ");
        sender.sendMessage("§6OBS.: §7As informações com <> são obrigatórios e [] são opcionais");
        sender.sendMessage(" ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage("§cUtilize: " + usage);
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player) {
            soundService.playSound((Player) sender, key);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/npc criar <id> <skin> [nome]");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) != null) {
            sender.sendMessage("§cJá existe um NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String skin = args[2];
        String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;

        npcService.createNpc(id, player.getLocation(), name, skin);
        sender.sendMessage("§aNPC '" + id + "' criado com sucesso na sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/npc clone <id original> <novo id>");
            return;
        }
        String originalId = args[1];
        String newId = args[2];

        if (npcService.getNpcEntry(originalId) == null) {
            sender.sendMessage("§cO NPC original com ID '" + originalId + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (npcService.getNpcEntry(newId) != null) {
            sender.sendMessage("§cJá existe um NPC com o novo ID '" + newId + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        npcService.cloneNpc(originalId, newId, player.getLocation());
        sender.sendMessage("§aNPC '" + originalId + "' clonado para '" + newId + "' na sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc remover <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.removeNpc(id);
        sender.sendMessage("§aNPC '" + id + "' removido com sucesso!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc rename <id> <novo nome>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        npcService.renameNpc(id, newName);
        sender.sendMessage("§aNome do NPC '" + id + "' alterado para '" + newName + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = npcService.getAllNpcIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§eNão há NPCs criados no momento.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }
        sender.sendMessage("§6Lista de NPCs existentes: §7" + String.join(", ", ids));
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String locationStr = String.format("%.2f, %.2f, %.2f em %s", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld());
        String lookAtPlayer = Boolean.TRUE.equals(entry.getIsMovible()) ? "§aAtivado" : "§cDesativado";
        String nameVisible = Boolean.TRUE.equals(entry.getHologramVisible()) ? "§aVisível" : "§cOculto";

        sender.sendMessage(" ");
        sender.sendMessage("§6Informações sobre o NPC §e" + id);
        sender.sendMessage("§f ID: §7" + entry.getId());
        sender.sendMessage("§f Skin: §7" + entry.getItem());
        sender.sendMessage("§f Nome: §7" + entry.getMessage());
        sender.sendMessage("§f Localização: §7" + locationStr);
        sender.sendMessage("§f Olhar para Jogador: " + lookAtPlayer);
        sender.sendMessage("§f Visibilidade do Nome: " + nameVisible);
        sender.sendMessage("§f Linhas de Texto (" + entry.getLines().size() + "):");
        for (int i = 0; i < entry.getLines().size(); i++) {
            sender.sendMessage("  §e" + (i + 1) + ": §r" + entry.getLines().get(i));
        }
        sender.sendMessage(" ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, "/npc tphere <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.teleportNpc(id, player.getLocation());
        sender.sendMessage("§aNPC '" + id + "' teleportado para a sua localização!");
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.updateNpcSkin(id, skin);
        sender.sendMessage("§aSkin do NPC '" + id + "' atualizada para '" + skin + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleName(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/npc togglename <id>");
            return;
        }
        String id = args[1];
        if (npcService.getNpcById(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = npcService.toggleNameVisibility(id);
        sender.sendMessage("§aA visibilidade do nome do NPC '" + id + "' foi " + (newState ? "§aativada§a." : "§cdesativada§a."));
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = npcService.toggleLookAtPlayer(id);
        sender.sendMessage("§aA função 'olhar para o jogador' do NPC '" + id + "' foi " + (newState ? "§aativada§a." : "§cdesativada§a."));
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.addLine(id, text);
        sender.sendMessage("§aLinha adicionada ao NPC '" + id + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/npc setline <id> <linha> <texto>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!npcService.setLine(id, line, text)) {
            sender.sendMessage("§cNão foi encontrado um NPC com o ID '" + id + "' ou a linha " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aLinha " + line + " do NPC '" + id + "' atualizada!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc removeline <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (!npcService.removeLine(id, line)) {
            sender.sendMessage("§cNão foi encontrado um NPC com o ID '" + id + "' ou a linha " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aLinha " + line + " do NPC '" + id + "' removida!");
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        npcService.addAction(id, action);
        sender.sendMessage("§aAção adicionada ao NPC '" + id + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/npc removeaction <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha da ação deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (!npcService.removeAction(id, line)) {
            sender.sendMessage("§cNão foi encontrado um NPC com o ID '" + id + "' ou a ação " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aAção " + line + " do NPC '" + id + "' removida!");
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
            sender.sendMessage("§cNão foi encontrado nenhum NPC com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        List<String> actions = entry.getActions();
        if (actions == null || actions.isEmpty()) {
            sender.sendMessage("§eO NPC '" + id + "' não possui ações de clique.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }

        sender.sendMessage("§6Ações de clique para o NPC §e" + id);
        for (int i = 0; i < actions.size(); i++) {
            sender.sendMessage("  §e" + (i + 1) + ": §7" + actions.get(i));
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