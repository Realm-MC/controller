package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.RawMessage;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.extensions.PlayerExtensions;
import lombok.experimental.ExtensionMethod;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@ExtensionMethod(PlayerExtensions.class)
@Cmd(cmd = "test", aliases = {"t"}, onlyPlayer = true)
public class TestCommand implements CommandInterface {
    
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;

        player.msg(MessageKey.TEST_WELCOME);

        RawMessage customMessage = Message.raw("<gradient:blue:purple><b>Teste Personalizado!</b></gradient>")
                .placeholder("player", player.getName());
        player.msg(customMessage);

        player.success("Comando executado com sucesso!");
        player.warning("Esta é uma mensagem de aviso");
        player.error("Esta é uma mensagem de erro");
        player.info("Esta é uma mensagem informativa");

        player.msg("<rainbow>Mensagem colorida!</rainbow>");

        player.msg("<red>Falha ao abrir ProfileMenu: " + "erro simulado");
    }
}