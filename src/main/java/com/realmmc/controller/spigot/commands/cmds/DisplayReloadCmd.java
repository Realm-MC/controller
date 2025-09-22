package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.display.DisplayItemService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Cmd(cmd = "displayreload", aliases = {"dreload"})
public class DisplayReloadCmd implements CommandInterface {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSomente jogadores podem usar este comando.");
            return;
        }

        Player player = (Player) sender;
        DisplayItemService service = Main.getInstance().getDisplayItemService();

        try {
            service.reload();
            player.sendMessage("§aConfiguração dos displays recarregada com sucesso!");
            player.sendMessage("§7Todos os displays foram removidos e recriados com base no arquivo displays.yml");
        } catch (Exception e) {
            player.sendMessage("§cErro ao recarregar configurações: " + e.getMessage());
            e.printStackTrace();
        }
    }
}