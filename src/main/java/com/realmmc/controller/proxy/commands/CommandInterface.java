package com.realmmc.controller.proxy.commands;

import com.velocitypowered.api.command.CommandSource;

import java.util.Collections;
import java.util.List;

public interface CommandInterface {
    void execute(CommandSource sender, String label, String[] args);

    default List<String> tabComplete(CommandSource sender, String[] args) {
        return Collections.emptyList();
    }
}
