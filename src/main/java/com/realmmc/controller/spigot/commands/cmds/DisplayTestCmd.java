package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.display.DisplayItemService;
import com.realmmc.controller.spigot.display.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.display.config.DisplayEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

@Cmd(cmd = "displaytest", aliases = {})
public class DisplayTestCmd implements CommandInterface {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return;
        }

        Player player = (Player) sender;
        
        try {
            DisplayItemService displayItemService = Main.getInstance().getDisplayItemService();
            DisplayConfigLoader displayConfigLoader = Main.getInstance().getDisplayConfigLoader();
            
            if (displayItemService == null) {
                player.sendMessage("§cErro: DisplayItemService não está disponível!");
                return;
            }
            
            if (displayConfigLoader == null) {
                player.sendMessage("§cErro: DisplayConfigLoader não está disponível!");
                return;
            }
            
            Location location = player.getLocation().add(0, 1.4, 0);

            ItemStack item = new ItemStack(Material.DIAMOND);
            List<String> lines = Arrays.asList(
                    "<yellow><b>Display de Teste</b>",
                    "<gray>Criado via comando",
                    "<white>Item: <aqua>Diamante"
            );

            displayItemService.show(player, location, item, lines, true);

            DisplayEntry entry = new DisplayEntry();
            entry.setType(DisplayEntry.Type.DISPLAY_ITEM);
            entry.setWorld(location.getWorld().getName());
            entry.setX(location.getX());
            entry.setY(location.getY());
            entry.setZ(location.getZ());
            entry.setYaw(location.getYaw());
            entry.setPitch(location.getPitch());
            entry.setItem(item.getType().name());

            displayConfigLoader.addEntry(entry);
            displayConfigLoader.save();

            player.sendMessage("§aDisplay criado e salvo no displays.yml!");
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar display: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
