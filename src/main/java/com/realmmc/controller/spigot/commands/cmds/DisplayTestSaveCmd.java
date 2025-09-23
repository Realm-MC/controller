package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

@Cmd(cmd = "displaytest", aliases = {"dtest"})
public class DisplayTestSaveCmd implements CommandInterface {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando.");
            return;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "display_item":
                handleDisplayItem(player, args);
                break;
            case "npc":
                handleNPC(player, args);
                break;
            case "hologram":
                handleHologram(player, args);
                break;
            default:
                showHelp(player);
                break;
        }
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§e§l=== /dtest - Comandos de Teste ===");
        player.sendMessage("§7/dtest display_item [MATERIAL] [SCALE] §8- Cria um display item");
        player.sendMessage("§7/dtest npc [nick] §8- Cria um NPC");
        player.sendMessage("§7/dtest hologram <texto> §8- Cria um holograma");
        player.sendMessage("§8Padrões: Material=DIAMOND, Scale=3.0, Nick=seu nick");
    }
    
    private void handleDisplayItem(Player player, String[] args) {
        Material material = Material.DIAMOND;
        float scale = 3.0f;

        if (args.length > 1) {
            try {
                material = Material.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cMaterial inválido: " + args[1]);
                player.sendMessage("§7Usando material padrão: DIAMOND");
            }
        }

        if (args.length > 2) {
            try {
                scale = Float.parseFloat(args[2]);
                if (scale <= 0) {
                    player.sendMessage("§cEscala deve ser maior que 0!");
                    scale = 3.0f;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cEscala inválida: " + args[2]);
                player.sendMessage("§7Usando escala padrão: 3.0");
            }
        }
        
        DisplayItemService service = Main.getInstance().getDisplayItemService();
        
        List<String> testLines = Arrays.asList(
                "<gradient:blue:purple><b>Display de Teste</b></gradient>",
                "<yellow>Material: " + material.name() + "</yellow>",
                "<green>Escala: <aqua>" + scale + "</aqua></green>"
        );

        ItemStack testItem = new ItemStack(material);

        try {
            service.showGlobal(player.getLocation(), testItem, testLines, true, Display.Billboard.CENTER, scale);
            
            player.sendMessage("§aDisplay item criado com sucesso!");
            player.sendMessage("§7Material: §e" + material.name() + " §7| Escala: §e" + scale);
            player.sendMessage("§7Salvo em displays.yml");
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar display item: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleNPC(Player player, String[] args) {
        String skin = player.getName();
        
        if (args.length > 1) {
            skin = args[1];
        }
        
        NPCService service = Main.getInstance().getNPCService();
        
        try {
            service.spawnGlobal(player.getLocation(), "NPC Teste", skin);
            
            player.sendMessage("§aNPC criado com sucesso!");
            player.sendMessage("§7Nome: §eNPC Teste §7| Skin: §e" + skin);
            player.sendMessage("§7Salvo em npcs.yml");
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleHologram(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUso: /dtest hologram <texto>");
            return;
        }

        StringBuilder textBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) textBuilder.append(" ");
            textBuilder.append(args[i]);
        }
        String text = textBuilder.toString();
        
        HologramService service = Main.getInstance().getHologramService();
        
        List<String> lines = Arrays.asList(
                "<gradient:gold:yellow><b>Holograma Teste</b></gradient>",
                "<white>" + text + "</white>",
                "<gray><i>Criado via comando</i></gray>"
        );
        
        try {
            service.showGlobal(player.getLocation().add(0, 2, 0), lines, false);
            
            player.sendMessage("§aHolograma criado com sucesso!");
            player.sendMessage("§7Texto: §e" + text);
            player.sendMessage("§7Salvo em holograms.yml");
            player.sendMessage("§6§lDica: §7Para editar o holograma, modifique o arquivo holograms.yml e use /hologramreload");
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar holograma: " + e.getMessage());
            e.printStackTrace();
        }
    }
}