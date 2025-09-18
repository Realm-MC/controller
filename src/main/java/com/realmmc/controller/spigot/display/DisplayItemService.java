package com.realmmc.controller.spigot.display;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;

public class DisplayItemService {
    // TODO: TEST AND CLEANCODE TOMORROW

    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public void clear(Player player) {
        List<UUID> ids = spawnedByPlayer.remove(player.getUniqueId());
        if (ids == null) return;
        for (UUID uuid : ids) {
            Entity e = findEntity(uuid);
            if (e != null && !e.isDead()) e.remove();
        }
    }

    public void moveHorizontal(Player player, double dx, double dz) {
        List<UUID> ids = spawnedByPlayer.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) return;
        for (UUID uuid : ids) {
            Entity e = findEntity(uuid);
            if (e == null || e.isDead()) continue;
            Location l = e.getLocation();
            Location target = new Location(l.getWorld(), l.getX() + dx, l.getY(), l.getZ() + dz, l.getYaw(), l.getPitch());
            e.teleport(target);
        }
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow) {
        show(player, base, item, lines, glow, Display.Billboard.CENTER, 1.2f);
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow,
                     Display.Billboard billboard, float scale) {
        ItemDisplay display = base.getWorld().spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(item == null ? new ItemStack(Material.DIAMOND) : item);
            d.setBillboard(billboard == null ? Display.Billboard.CENTER : billboard);
            d.setShadowStrength(0.0f);
            d.setBrightness(new Display.Brightness(15, 15));
            Transformation nt = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
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
                    Component comp = mm.deserialize(line);
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

        spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(uuids);
    }
    // TODO: TEST AND CLEANCODE TOMORROW
    private Entity findEntity(UUID id) {
        for (var w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
