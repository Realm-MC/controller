package com.realmmc.controller.spigot.entities.holograms;

import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.HologramConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;

public class HologramService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HologramConfigLoader configLoader;

    public HologramService() {
        this.configLoader = new HologramConfigLoader();
        this.configLoader.load();
        try { clearAll(); } catch (Throwable ignored) {}
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
        if (entry.getActions() == null) {
            entry.setActions(new ArrayList<>());
        }

        configLoader.addEntry(entry);
        configLoader.save();

        showWithoutSaving(base, lines, glow);
    }

    public void clear(Player player) {
        List<UUID> entities = spawnedByPlayer.get(player.getUniqueId());
        if (entities != null) {
            for (UUID entityId : entities) {
                Entity entity = findEntityByUuid(entityId);
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
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay &&
                        (entity.getScoreboardTags().contains("controller_hologram_line") ||
                         entity.getScoreboardTags().contains("controller_npc_name_line"))) {
                    entity.remove();
                }
            }
        }
        try {
            for (DisplayEntry e : configLoader.getEntries()) {
                if (e.getType() != DisplayEntry.Type.HOLOGRAM) continue;
                World w = Bukkit.getWorld(e.getWorld());
                if (w == null) continue;
                Location base = new Location(w, e.getX(), e.getY(), e.getZ());
                double horizRadiusSq = 0.7 * 0.7;
                double minY = e.getY() - 1.0;
                int linesCount = (e.getLines() != null && !e.getLines().isEmpty()) ? e.getLines().size() : 1;
                double maxY = e.getY() + (linesCount * 0.35) + 1.0;
                for (Entity ent : w.getEntitiesByClass(TextDisplay.class)) {
                    Location loc = ent.getLocation();
                    if (loc.getY() < minY || loc.getY() > maxY) continue;
                    double dx = loc.getX() - base.getX();
                    double dz = loc.getZ() - base.getZ();
                    if ((dx * dx + dz * dz) <= horizRadiusSq) {
                        ent.remove();
                    }
                }
            }
        } catch (Throwable ignored) {}
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

    private List<UUID> showWithoutSaving(Location base, List<String> lines, boolean glow) {
        List<UUID> ids = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return ids;
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
            textDisplay.customName(null);
            textDisplay.setCustomNameVisible(false);
            textDisplay.addScoreboardTag("controller_hologram_line");
            ids.add(textDisplay.getUniqueId());
        }
        return ids;
    }

    public List<UUID> spawnTemporary(Location base, List<String> lines, boolean glow) {
        return showWithoutSaving(base, lines, glow);
    }

    public void removeByUUIDs(Collection<UUID> ids) {
        if (ids == null) return;
        for (World world : Bukkit.getWorlds()) {
            for (UUID id : ids) {
                Entity e = world.getEntity(id);
                if (e != null) e.remove();
            }
        }
    }

    public void addTagToUUIDs(Collection<UUID> ids, String tag) {
        if (ids == null || tag == null) return;
        for (World world : Bukkit.getWorlds()) {
            for (UUID id : ids) {
                Entity e = world.getEntity(id);
                if (e instanceof TextDisplay) {
                    e.addScoreboardTag(tag);
                }
            }
        }
    }

    private Entity findEntityByUuid(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uuid)) {
                    return entity;
                }
            }
        }
        return null;
    }
}