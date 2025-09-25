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
            Messages.send(sender, "<red>Você не tem permissão para usar este comando.");
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        if ("criar".equals(subCommand)) {
            handleCriar(sender, args);
        } else {
            Messages.send(sender, "<red>Subcomando desconhecido. Use /npc para ver a ajuda.");
        }
    }

    private void handleCriar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este comando só pode ser executado por um jogador.");
            return;
        }

        if (args.length < 4) {
            Messages.send(player, "<red>Uso incorreto! Use: /npc criar <id> <skin> <nome_de_exibicao>");
            return;
        }

        String id = args[1];
        String skinNick = args[2];
        String displayName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        try {
            NPCService npcService = Main.getInstance().getNPCService();
            npcService.spawnGlobal(id, player.getLocation(), displayName, skinNick);
            Messages.send(player, "<green>NPC '" + id + "' criado com sucesso!");
        } catch (Exception e) {
            Messages.send(player, "<red>Ocorreu um erro ao criar o NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /npc ---");
        Messages.send(sender, "<#FFFF00>/npc criar <id> <skin> <nome_de_exibicao> <#777777>- Cria um NPC.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("controller.npc.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("criar"), new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("criar")) {
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}