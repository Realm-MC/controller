package com.realmmc.controller.proxy.commands.cmds;

import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

@Cmd(cmd = "test", aliases = {"t"}, onlyPlayer = true)
public class TestCommand implements CommandInterface {
    MiniMessage mm = MiniMessage.miniMessage();
    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        Player player = (Player) sender;
        if (player != null) {
            player.sendMessage(mm.deserialize("<red>Test command"));
            return;
        }
    }
}
