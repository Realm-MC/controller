package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Display;

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

        if (type == DisplayEntry.Type.DISPLAY_ITEM) {
            if (args.length < 2) {
                Messages.send(player, "<red>Uso: /display DISPLAY_ITEM <material> [text...]");
                return;
            }
            String matArg = args[1];
            String text = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            try {
                Material mat = Material.valueOf(matArg.toUpperCase());
                ItemStack item = new ItemStack(mat);
                List<String> lines = text == null || text.isBlank() ? java.util.Collections.emptyList() : java.util.List.of(text);
                DisplayItemService svc = Main.getInstance().getDisplayItemService();
                String genId = "disp_" + System.currentTimeMillis();
                svc.show(player, player.getLocation(), item, lines, false, Display.Billboard.CENTER, 3.0f, genId);
                Messages.send(player, "<green>Display Item criado com sucesso!");
            } catch (IllegalArgumentException ex) {
                Messages.send(player, "<red>Material inválido: " + matArg);
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar Display Item: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        if (type == DisplayEntry.Type.HOLOGRAM) {
            if (args.length < 2) {
                Messages.send(player, "<red>Uso: /display HOLOGRAM <text or line1|line2|...>");
                return;
            }
            String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            List<String> lines;
            if (joined.contains("|")) {
                lines = new java.util.ArrayList<>();
                for (String part : joined.split("\\|")) {
                    String s = part.trim();
                    if (!s.isEmpty()) lines.add(s);
                }
            } else {
                lines = java.util.List.of(joined);
            }
            try {
                HologramService svc = Main.getInstance().getHologramService();
                svc.showGlobal(player.getLocation(), lines, false);
                Messages.send(player, "<green>Holograma criado com sucesso!");
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar Holograma: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        Messages.send(player, "<red>Tipo '" + type.name() + "' ainda não implementado aqui.");
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /display ---");
        Messages.send(sender, "<#FFFF00>/display NPC <id> <skin> <name> <#777777>- Cria um NPC no seu local.");
        Messages.send(sender, "<#FFFF00>/display DISPLAY_ITEM <material> [text...] <#777777>- Cria um item display.");
        Messages.send(sender, "<#FFFF00>/display HOLOGRAM <text or line1|line2|...> <#777777>- Cria um holograma.");
        Messages.send(sender, "<gray>Types: DISPLAY_ITEM, HOLOGRAM, NPC (case-insensitive)");
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
