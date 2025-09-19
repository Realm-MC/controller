package com.realmmc.controller.spigot.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class DisplayItemService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    public void show(Player player, Location location, ItemStack item, List<String> lines, boolean persistent) {
        show(player, location, item, lines, true, Display.Billboard.VERTICAL, 3.0f);
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow,
                     Display.Billboard billboard, float scale) {
        List<UUID> entities = new ArrayList<>();
        
        ItemDisplay itemDisplay = base.getWorld().spawn(base, ItemDisplay.class);
        itemDisplay.setItemStack(item != null ? item : new ItemStack(Material.DIAMOND));
        itemDisplay.setBillboard(billboard);
        itemDisplay.setShadowStrength(0.0f);
        itemDisplay.setBrightness(new Display.Brightness(15, 15));
        itemDisplay.setGlowing(glow);
        
        Transformation transformation = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new Quaternionf(0, 0, 0, 1)
        );
        itemDisplay.setTransformation(transformation);
        entities.add(itemDisplay.getUniqueId());

        if (lines != null && !lines.isEmpty()) {
            double baseY = base.getY() + (scale * 0.5);
            double step = 0.25;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                double textY = baseY + (lines.size() - 1 - i) * step;
                Location textLocation = new Location(base.getWorld(), base.getX(), textY, base.getZ());
                
                TextDisplay textDisplay = base.getWorld().spawn(textLocation, TextDisplay.class);
                Component component = miniMessage.deserialize(line);
                textDisplay.text(component);
                textDisplay.setBillboard(Display.Billboard.CENTER);
                textDisplay.setSeeThrough(true);
                textDisplay.setDefaultBackground(false);
                textDisplay.setShadowed(false);
                textDisplay.setLineWidth(200);
                textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
                textDisplay.setGlowing(glow);
                textDisplay.setBrightness(new Display.Brightness(15, 15));
                textDisplay.setViewRange(32f);
                
                entities.add(textDisplay.getUniqueId());
            }
        }

        if (player != null) {
            spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(entities);
        }
    }

    public void clear(Player player) {
        if (player == null) return;
        
        List<UUID> entityIds = spawnedByPlayer.remove(player.getUniqueId());
        if (entityIds == null || entityIds.isEmpty()) return;
        
        for (UUID uuid : entityIds) {
            Entity entity = findEntityByUuid(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
    }

    public void moveHorizontal(Player player, double deltaX, double deltaZ) {
        if (player == null) return;
        
        List<UUID> entityIds = spawnedByPlayer.get(player.getUniqueId());
        if (entityIds == null || entityIds.isEmpty()) return;
        
        for (UUID uuid : entityIds) {
            Entity entity = findEntityByUuid(uuid);
            if (entity == null || entity.isDead()) continue;
            
            Location currentLocation = entity.getLocation();
            Location newLocation = new Location(
                currentLocation.getWorld(),
                currentLocation.getX() + deltaX,
                currentLocation.getY(),
                currentLocation.getZ() + deltaZ,
                currentLocation.getYaw(),
                currentLocation.getPitch()
            );
            entity.teleport(newLocation);
        }
    }

    public void clearAll() {
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(ItemDisplay.class).forEach(Entity::remove);
            world.getEntitiesByClass(TextDisplay.class).forEach(Entity::remove);
        }
        spawnedByPlayer.clear();
    }

    public ItemDisplay findEntity(Location location, double radius) {
        return location.getWorld().getNearbyEntitiesByType(ItemDisplay.class, location, radius)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Entity findEntityByUuid(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }
}
