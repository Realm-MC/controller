package com.realmmc.controller.spigot.commands;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface CommandInterface {
    void execute(CommandSender sender, String label, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
