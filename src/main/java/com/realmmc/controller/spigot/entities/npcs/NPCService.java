package com.realmmc.controller.spigot.entities.npcs;

import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class NPCService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final NPCConfigLoader configLoader;

    public NPCService() {
        this.configLoader = new NPCConfigLoader();
    }

    public void spawn(Player player, Location location, String name) {
        spawn(player, location, name, "default");
    }

    public void spawn(Player player, Location location, String name, String skin) {
        List<UUID> entities = new ArrayList<>();
        
        spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(entities);
    }

    public void spawnGlobal(Location location, String name, String skin) {
        DisplayEntry entry = new DisplayEntry();
        entry.setType(DisplayEntry.Type.NPC);
        entry.setWorld(location.getWorld().getName());
        entry.setX(location.getX());
        entry.setY(location.getY());
        entry.setZ(location.getZ());
        entry.setYaw(location.getYaw());
        entry.setPitch(location.getPitch());
        entry.setMessage(name);

        configLoader.addEntry(entry);
        configLoader.save();
    }

    public void remove(Player player) {
        List<UUID> entities = spawnedByPlayer.get(player.getUniqueId());
        if (entities != null) {
            for (UUID entityId : entities) {
                org.bukkit.entity.Entity entity = findEntityByUuid(entityId);
                if (entity != null) {
                    entity.remove();
                }
            }
            entities.clear();
        }
    }

    private org.bukkit.entity.Entity findEntityByUuid(UUID uuid) {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uuid)) {
                    return entity;
                }
            }
        }
        return null;
    }
}