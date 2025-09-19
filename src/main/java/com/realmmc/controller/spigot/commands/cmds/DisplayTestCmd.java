package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.display.DisplayItemService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Cmd(cmd = "displaytest", aliases = {})
public class DisplayTestCmd implements CommandInterface {
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSomente jogadores.");
            return;
        }
        Player p = (Player) sender;
        DisplayItemService service = Main.getInstance().getDisplayItemService();

        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            service.clear(p);
            p.sendMessage("§aDisplay limpo.");
            return;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) item = new ItemStack(Material.DIAMOND);

        Location base = p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(1.0)).add(0, 0.2, 0);

        List<String> lines = new ArrayList<>(Arrays.asList(
                "<yellow><b>Display Test</b>",
                "<gray>Item vertical 4x + Holograma center",
                "<white>Item: <aqua>" + item.getType().name()
        ));

        service.show(p, base, item, lines, false);
        p.sendMessage("§aDisplay criado. Use /" + label + " clear para remover.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("clear");
        return List.of();
    }
}
