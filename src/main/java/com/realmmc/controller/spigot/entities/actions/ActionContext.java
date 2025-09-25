package com.realmmc.controller.spigot.entities.actions;

import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ActionContext {
    private final Player player;
    private final DisplayEntry entry;
    private final Location location;

    public ActionContext(Player player, DisplayEntry entry, Location location) {
        this.player = player;
        this.entry = entry;
        this.location = location;
    }

    public Player getPlayer() { return player; }
    public Optional<DisplayEntry> getEntry() { return Optional.ofNullable(entry); }
    public Optional<Location> getLocation() { return Optional.ofNullable(location); }
}
