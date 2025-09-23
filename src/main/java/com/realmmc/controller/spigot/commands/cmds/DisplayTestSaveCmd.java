package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
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
        DisplayItemService service = Main.getInstance().getDisplayItemService();

        List<String> testLines = Arrays.asList(
                "<gradient:blue:purple><b>Display de Teste</b></gradient>",
                "<yellow>Criado via comando</yellow>",
                "<green>Item: <aqua>Diamante</aqua></green>"
        );

        ItemStack testItem = new ItemStack(Material.DIAMOND);

        try {
            service.show(player, player.getLocation(), testItem, testLines, true, Display.Billboard.CENTER, 3.0f);
            
            player.sendMessage("§aDisplay de teste criado com sucesso!");
            player.sendMessage("§7O display foi salvo no arquivo displays.yml");
            player.sendMessage("§7Use §e/displayreload §7para testar o carregamento");
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar display de teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
}