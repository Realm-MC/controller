package com.realmmc.controller.spigot.display;

import com.realmmc.controller.spigot.display.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.display.config.DisplayEntry;
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
    private final DisplayConfigLoader configLoader;

    public DisplayItemService() {
        this.configLoader = new DisplayConfigLoader();
        this.configLoader.load();
        loadSavedDisplays();
    }

    private void loadSavedDisplays() {
        for (DisplayEntry entry : configLoader.getEntries()) {
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

    public void show(Player player, Location location, ItemStack item, List<String> lines, boolean persistent) {
        show(player, location, item, lines, true, Display.Billboard.VERTICAL, 3.0f);
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow,
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
                textDisplay.setBrightness(new Display.Brightness(15, 15));
                textDisplay.setViewRange(32f);

                entities.add(textDisplay.getUniqueId());
            }
        }

        DisplayEntry entry = new DisplayEntry();
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

        if (player != null) {
            spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(entities);
        }
    }

    public void showGlobal(Location base, ItemStack item, List<String> lines, boolean glow) {
        ItemDisplay display = base.getWorld().spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(item == null ? new ItemStack(Material.DIAMOND) : item);
            d.setBillboard(Display.Billboard.VERTICAL);
            try {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            } catch (NoSuchMethodError ignored) {
            }
            d.setShadowStrength(0.0f);
            d.setBrightness(new Display.Brightness(15, 15));
            Transformation nt = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(0, 0, 0, 1),
                    new Vector3f(4.0f, 4.0f, 4.0f),
                    new Quaternionf(0, 0, 0, 1)
            );
            d.setTransformation(nt);
            d.setGlowing(glow);
        });

        List<UUID> uuids = new ArrayList<>();
        uuids.add(display.getUniqueId());

        double y = base.getY() + 0.6;
        double step = 0.25;
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Location l = new Location(base.getWorld(), base.getX(), y + (lines.size() - 1 - i) * step, base.getZ(), base.getYaw(), base.getPitch());
                TextDisplay td = base.getWorld().spawn(l, TextDisplay.class, t -> {
                    Component comp = miniMessage.deserialize(line);
                    t.text(comp);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setSeeThrough(true);
                    t.setDefaultBackground(false);
                    t.setShadowed(false);
                    t.setLineWidth(200);
                    t.setAlignment(TextDisplay.TextAlignment.CENTER);
                    t.setGlowing(glow);
                    t.setBrightness(new Display.Brightness(15, 15));
                    t.setViewRange(32f);
                });
                uuids.add(td.getUniqueId());
            }
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

    public void reload() {
        clearAll();

        configLoader.load();

        loadSavedDisplays();
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
