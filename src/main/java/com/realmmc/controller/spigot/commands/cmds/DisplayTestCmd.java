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
import org.bukkit.entity.Display;

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
        DisplayItemService service = Main.getDisplayItemService();

        if (args.length > 0 && args[0].equalsIgnoreCase("move")) {
            if (args.length < 3) {
                p.sendMessage("§eUso: /" + label + " move <dx> <dz>");
                return;
            }
            try {
                double dx = Double.parseDouble(args[1]);
                double dz = Double.parseDouble(args[2]);
                service.moveHorizontal(p, dx, dz);
                p.sendMessage("§aMovido: dx=" + dx + " dz=" + dz);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cValores inválidos. Use números, ex: /" + label + " move 0.5 -1");
            }
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            service.clear(p);
            p.sendMessage("§aDisplay limpo.");
            return;
        }

        boolean glow = false;
        Display.Billboard billboard = null;
        float scale = 1.2f;

        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "vertical":
                case "v":
                    billboard = Display.Billboard.VERTICAL;
                    scale = 2.0f;
                    break;
                case "horizontal":
                case "h":
                    billboard = Display.Billboard.HORIZONTAL;
                    scale = 2.0f;
                    break;
                case "center":
                case "c":
                    billboard = Display.Billboard.CENTER;
                    break;
                case "glow":
                case "true":
                    glow = true;
                    break;
                default:
                    billboard = Display.Billboard.VERTICAL;
                    scale = 2.0f;
                    break;
            }
            if (args.length > 1 && (args[1].equalsIgnoreCase("glow") || args[1].equalsIgnoreCase("true"))) {
                glow = true;
            }
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) item = new ItemStack(Material.DIAMOND);

        Location base = p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(1.0)).add(0, 0.2, 0);

        List<String> lines = new ArrayList<>(Arrays.asList(
                "<yellow><b>Display Test</b>",
                "<gray>Glow: " + (glow ? "<green>ON" : "<red>OFF") + (billboard != null ? " <gray>Mode: <aqua>" + billboard.name() + "</aqua> x" + scale : ""),
                "<white>Item: <aqua>" + item.getType().name()
        ));

        if (billboard == null) {
            service.show(p, base, item, lines, glow);
        } else {
            service.show(p, base, item, lines, glow, billboard, scale);
        }
        p.sendMessage("§aDisplay criado. Use /" + label + " clear para remover.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("clear", "move", "glow", "true", "vertical", "horizontal", "center");
        }
        if (args.length == 2 && "move".equalsIgnoreCase(args[0])) return List.of("0.5");
        if (args.length == 3 && "move".equalsIgnoreCase(args[0])) return List.of("-1");
        if (args.length == 2 && ("vertical".equalsIgnoreCase(args[0]) || "horizontal".equalsIgnoreCase(args[0]) || "center".equalsIgnoreCase(args[0])))
            return Arrays.asList("glow", "true");
        return CommandInterface.super.tabComplete(sender, args);
    }
}
