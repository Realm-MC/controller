package com.palacesky.controller.spigot.entities.actions;

import org.bukkit.entity.Player;

public interface Action {
    void execute(Player player, ActionContext ctx);
}