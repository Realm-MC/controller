package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Cmd(cmd = "npc", aliases = {})
public class NpcCommand implements CommandInterface {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("controller.manager")) {
            Messages.send(sender, "<red>Apenas o grupo Gerente ou superior pode executar este comando.");
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar":
                handleCriar(sender, args);
                break;
            case "skin":
                handleSkin(sender, args);
                break;
            default:
                Messages.send(sender, "<red>Subcomando desconhecido. Use /npc para ver a ajuda.");
                break;
        }
    }

    private void handleCriar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este comando só pode ser executado por um jogador.");
            return;
        }

        if (args.length < 4) {
            Messages.send(player, "<red>Uso: /npc criar <id> <skin_url|nick|player> <nome_de_exibicao>");
            return;
        }

        String id = args[1];
        String skinSource = args[2];
        String displayName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        try {
            NPCService npcService = Main.getInstance().getNPCService();
            if (npcService.getNpcById(id) != null) {
                Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
                return;
            }

            npcService.spawnGlobal(id, player.getLocation(), displayName, skinSource);
            Messages.send(player, "<green>NPC '" + id + "' criado com sucesso!");
        } catch (Exception e) {
            Messages.send(player, "<red>Ocorreu um erro ao criar o NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "<red>Uso: /npc skin <id> <nova_skin_url|nick|player>");
            return;
        }

        String id = args[1];
        String newSkinSource = args[2];

        try {
            NPCService npcService = Main.getInstance().getNPCService();
            if (npcService.getNpcById(id) == null) {
                Messages.send(sender, "<red>Nenhum NPC encontrado com o ID '" + id + "'.");
                return;
            }

            npcService.updateNpcSkin(id, newSkinSource);
            Messages.send(sender, "<green>A skin do NPC '" + id + "' foi atualizada com sucesso!");
        } catch (Exception e) {
            Messages.send(sender, "<red>Ocorreu um erro ao atualizar a skin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /npc ---");
        Messages.send(sender, "<#FFFF00>/npc criar <id> <skin> <nome> <#777777>- Cria um NPC.");
        Messages.send(sender, "<#FFFF00>/npc skin <id> <nova_skin> <#777777>- Altera a skin de um NPC.");
        Messages.send(sender, "<gray>Para a skin, você pode usar um nick, uma URL de imagem .png ou a palavra 'player'.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("controller.npc.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("criar", "skin"), new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
            NPCService npcService = Main.getInstance().getNPCService();
            return StringUtil.copyPartialMatches(args[1], npcService.getAllNpcIds(), new ArrayList<>());
        }

        if ((args[0].equalsIgnoreCase("criar") && args.length == 3) || (args[0].equalsIgnoreCase("skin") && args.length == 3)) {
            List<String> suggestions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestions.add(p.getName());
            }
            suggestions.add("player");
            return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}