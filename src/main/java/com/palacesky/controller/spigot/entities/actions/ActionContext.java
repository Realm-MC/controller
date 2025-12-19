package com.palacesky.controller.spigot.entities.actions;

import com.palacesky.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ActionContext {
    private final Player player;
    private final DisplayEntry entry;
    private final Location location;
    private final Map<String, String> labels;

    public ActionContext(Player player, DisplayEntry entry, Location location) {
        this.player = player;
        this.entry = entry;
        this.location = location;
        this.labels = Collections.emptyMap();
    }

    public ActionContext(Player player, DisplayEntry entry, Location location, Map<String, String> labels) {
        this.player = player;
        this.entry = entry;
        this.location = location;
        this.labels = labels == null ? Collections.emptyMap() : labels;
    }

    public Player getPlayer() {
        return player;
    }

    public Optional<DisplayEntry> getEntry() {
        return Optional.ofNullable(entry);
    }

    public Optional<Location> getLocation() {
        return Optional.ofNullable(location);
    }

    public Map<String, String> getLabels() {
        return labels;
    }
}
