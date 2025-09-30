package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Cmd(cmd = "display", aliases = {})
public class DisplayCommand implements CommandInterface {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("controller.manager")) {
            Messages.send(sender, "<red>Apenas o grupo Gerente ou superior pode executar este comando.");
            return;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este comando só pode ser executado por um jogador.");
            return;
        }

        if (args.length < 1) {
            showHelp(sender);
            return;
        }

        String typeArg = args[0];
        DisplayEntry.Type type = DisplayEntry.Type.fromString(typeArg);
        if (type == null) {
            Messages.send(player, "<red>Tipo desconhecido: " + typeArg + ". Use NPC/HOLOGRAM/DISPLAY_ITEM.");
            return;
        }

        if (type == DisplayEntry.Type.NPC) {
            if (args.length < 4) {
                Messages.send(player, "<red>Uso: /display NPC <id> <skin_url|nick|player> <name>");
                return;
            }
            String id = args[1];
            String skin = args[2];
            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            try {
                NPCService npcService = Main.getInstance().getNPCService();
                if (npcService.getNpcById(id) != null) {
                    Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
                    return;
                }
                npcService.spawnGlobal(id, player.getLocation(), name, skin);
                Messages.send(player, "<green>NPC '" + id + "' criado com sucesso!");
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar NPC: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        Messages.send(player, "<red>Tipo '" + type.name() + "' ainda não implementado aqui.");
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /display ---");
        Messages.send(sender, "<#FFFF00>/display <type> ... <#777777>- Apenas valida o type informado.");
        Messages.send(sender, "<gray>Types válidos: DISPLAY_ITEM, HOLOGRAM, NPC (case-insensitive)");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("controller.manager")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("NPC", "HOLOGRAM", "DISPLAY_ITEM"), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
