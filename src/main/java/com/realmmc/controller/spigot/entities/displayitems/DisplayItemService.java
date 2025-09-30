package com.realmmc.controller.spigot.entities.displayitems;

import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class DisplayItemService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DisplayConfigLoader configLoader;

    public DisplayItemService(DisplayConfigLoader configLoader) {
        this.configLoader = configLoader;
        loadSavedDisplays();
    }

    private void loadSavedDisplays() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            if (entry.getType() != DisplayEntry.Type.DISPLAY_ITEM) {
                continue;
            }

            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;

                Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(),
                        entry.getYaw(), entry.getPitch());

                ItemStack item = new ItemStack(Material.valueOf(entry.getItem()));
                Display.Billboard billboard = Display.Billboard.valueOf(entry.getBillboard());

                showWithoutSaving(location, item, entry.getLines(), entry.getGlow(), billboard, entry.getScale());

            } catch (Exception e) {
                System.err.println("Erro ao carregar display ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void showWithoutSaving(Location base, ItemStack item, List<String> lines, boolean glow,
                                   Display.Billboard billboard, float scale) {
        List<UUID> entities = new ArrayList<>();

        Location itemLocation = base.clone().add(0, 1.4, 0);

        ItemDisplay itemDisplay = base.getWorld().spawn(itemLocation, ItemDisplay.class);
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
            double baseY = itemLocation.getY() + (scale * 0.5);
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

                entities.add(textDisplay.getUniqueId());
            }
        }
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow, Display.Billboard billboard, float scale, String id) {
        DisplayEntry entry = new DisplayEntry();
        entry.setId(id);
        entry.setType(DisplayEntry.Type.DISPLAY_ITEM);
        entry.setWorld(base.getWorld().getName());
        entry.setX(base.getX());
        entry.setY(base.getY());
        entry.setZ(base.getZ());
        entry.setYaw(base.getYaw());
        entry.setPitch(base.getPitch());
        entry.setItem(item != null ? item.getType().name() : Material.DIAMOND.name());
        entry.setLines(lines);
        entry.setGlow(glow);
        entry.setBillboard(billboard.name());
        entry.setScale(scale);

        configLoader.addEntry(entry);
        configLoader.save();

        showWithoutSaving(base, item, lines, glow, billboard, scale);
    }

    public void reload() {
        configLoader.load();
        clearAll();
        loadSavedDisplays();
    }

    public void clearAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay || (entity instanceof TextDisplay && !entity.getScoreboardTags().contains("hologram_line"))) {
                    entity.remove();
                }
            }
        }
    }

    private Entity findEntityByUuid(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}