package com.realmmc.controller.spigot.entities.holograms;

import com.realmmc.controller.spigot.entities.config.HologramConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

public class HologramService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HologramConfigLoader configLoader;

    public HologramService() {
        this.configLoader = new HologramConfigLoader();
        this.configLoader.load();
        loadSavedHolograms();
    }

    public void show(Player player, Location location, List<String> lines) {
        show(player, location, lines, false);
    }

    public void show(Player player, Location base, List<String> lines, boolean glow) {
        List<UUID> entities = new ArrayList<>();

        if (lines != null && !lines.isEmpty()) {
            double step = 0.25;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                double textY = base.getY() + (lines.size() - 1 - i) * step;
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

                entities.add(textDisplay.getUniqueId());
            }
        }

        spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(entities);
    }

    public void showGlobal(Location base, List<String> lines, boolean glow) {
        DisplayEntry entry = new DisplayEntry();
        entry.setType(DisplayEntry.Type.HOLOGRAM);
        entry.setWorld(base.getWorld().getName());
        entry.setX(base.getX());
        entry.setY(base.getY());
        entry.setZ(base.getZ());
        entry.setYaw(base.getYaw());
        entry.setPitch(base.getPitch());
        entry.setLines(lines);
        entry.setGlow(glow);

        configLoader.addEntry(entry);
        configLoader.save();

        showWithoutSaving(base, lines, glow);
    }

    public void clear(Player player) {
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

    public void reload() {
        clearAll();
        configLoader.load();
        loadSavedHolograms();
    }

    public void clearAll() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay) {
                    entity.remove();
                }
            }
        }
    }

    private void loadSavedHolograms() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;
                Location base = new Location(world, entry.getX(), entry.getY(), entry.getZ(), entry.getYaw(), entry.getPitch());
                showWithoutSaving(base, entry.getLines(), Boolean.TRUE.equals(entry.getGlow()));
            } catch (Exception e) {
                System.err.println("Erro ao carregar holograma ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void showWithoutSaving(Location base, List<String> lines, boolean glow) {
        if (lines == null || lines.isEmpty()) return;
        double step = 0.25;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double textY = base.getY() + (lines.size() - 1 - i) * step;
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