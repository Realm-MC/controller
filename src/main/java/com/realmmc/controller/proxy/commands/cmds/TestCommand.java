package com.realmmc.controller.proxy.commands.cmds;

import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.velocitypowered.api.command.CommandSource;

@Cmd(cmd = "test", aliases = {"t"}, onlyPlayer = true)
public class TestCommand implements CommandInterface {
    @Override
    public void execute(CommandSource sender, String label, String[] args) {

    }
}
