package com.realmmc.controller.spigot.entities.holograms;

import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.HologramConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HologramService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final Map<String, List<UUID>> globalHolograms = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HologramConfigLoader configLoader;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final Logger logger = Main.getInstance().getLogger();

    public HologramService() {
        this.configLoader = new HologramConfigLoader();
        this.configLoader.load();
        try {
            clearAll();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Ocorreu um erro não crítico ao limpar hologramas na inicialização.", t);
        }
        loadSavedHolograms();
    }

    public void show(Player player, Location base, List<String> lines, boolean glow) {
        List<UUID> entities = showWithoutSaving(base, lines, glow);
        spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(entities);
    }

    public void showGlobal(String customId, Location base, List<String> lines, boolean glow) {
        DisplayEntry entry = new DisplayEntry();
        String id = (customId != null && !customId.isEmpty()) ? customId : "hologram_" + UUID.randomUUID().toString().substring(0, 8);
        entry.setId(id);

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

        removeGlobalHologram(entry.getId());
        List<UUID> uuids = showWithoutSaving(base, lines, glow);
        globalHolograms.put(entry.getId(), uuids);
    }

    public void clear(Player player) {
        List<UUID> entities = spawnedByPlayer.remove(player.getUniqueId());
        if (entities != null) {
            removeByUUIDs(entities);
        }
    }

    public void reload() {
        clearAll();
        configLoader.load();
        loadSavedHolograms();
    }

    public void clearAll() {
        for (List<UUID> entities : spawnedByPlayer.values()) {
            removeByUUIDs(entities);
        }
        spawnedByPlayer.clear();

        for (List<UUID> uuids : globalHolograms.values()) {
            removeByUUIDs(uuids);
        }
        globalHolograms.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains("controller_hologram_line")) {
                    entity.remove();
                }
            }
        }
    }

    private void removeGlobalHologram(String id) {
        List<UUID> uuids = globalHolograms.remove(id);
        if (uuids != null) {
            removeByUUIDs(uuids);
        }
    }

    private void loadSavedHolograms() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) {
                    logger.warning("Mundo '" + entry.getWorld() + "' não encontrado para o holograma ID " + entry.getId());
                    continue;
                }
                Location base = new Location(world, entry.getX(), entry.getY(), entry.getZ(), entry.getYaw(), entry.getPitch());

                removeGlobalHologram(entry.getId());

                List<UUID> uuids = showWithoutSaving(base, entry.getLines(), Boolean.TRUE.equals(entry.getGlow()));
                globalHolograms.put(entry.getId(), uuids);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao carregar holograma ID " + entry.getId(), e);
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

            textDisplay.setPersistent(false);

            Component component = miniMessage.deserialize(MiniMessage.miniMessage().serialize(legacySerializer.deserialize(line)));
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
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
    }

    public void addTagToUUIDs(Collection<UUID> ids, String tag) {
        if (ids == null || tag == null) return;
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof TextDisplay) {
                e.addScoreboardTag(tag);
            }
        }
    }

    private Entity findEntityByUuid(UUID uuid) {
        return Bukkit.getEntity(uuid);
    }

    public DisplayEntry getHologramEntry(String id) {
        return configLoader.getById(id);
    }

    public Set<String> getAllHologramIds() {
        return configLoader.getEntries().stream()
                .map(DisplayEntry::getId)
                .collect(Collectors.toSet());
    }

    public void removeHologram(String id) {
        if (configLoader.removeEntry(id)) {
            reload();
        }
    }

    public void teleportHologram(String id, Location location) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setWorld(location.getWorld().getName());
            entry.setX(location.getX());
            entry.setY(location.getY());
            entry.setZ(location.getZ());
            entry.setYaw(location.getYaw());
            entry.setPitch(location.getPitch());
            configLoader.addEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public boolean toggleGlow(String id) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            boolean newState = !Boolean.TRUE.equals(entry.getGlow());
            entry.setGlow(newState);
            configLoader.addEntry(entry);
            configLoader.save();
            reload();
            return newState;
        }
        return false;
    }

    public void addLine(String id, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = (entry.getLines() != null) ? new ArrayList<>(entry.getLines()) : new ArrayList<>();
            lines.add(text);
            entry.setLines(lines);
            configLoader.addEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public boolean setLine(String id, int lineIndex, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.set(lineIndex - 1, text);
                entry.setLines(lines);
                configLoader.addEntry(entry);
                configLoader.save();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean removeLine(String id, int lineIndex) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.remove(lineIndex - 1);
                entry.setLines(lines);
                configLoader.addEntry(entry);
                configLoader.save();
                reload();
                return true;
            }
        }
        return false;
    }
}