package com.realmmc.controller.spigot.entities.config;

import com.realmmc.controller.spigot.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class HologramConfigLoader {
    private final Logger logger = Main.getInstance().getLogger();
    private final File configFile = new File(Main.getInstance().getDataFolder(), "holograms.yml");
    private YamlConfiguration config;
    private final Map<String, DisplayEntry> entries = new HashMap<>();

    public void load() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                logger.info("Arquivo holograms.yml criado.");
            } catch (IOException e) {
                logger.severe("Erro ao criar holograms.yml: " + e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        entries.clear();

        ConfigurationSection entriesSection = config.getConfigurationSection("entries");
        if (entriesSection != null) {
            for (String id : entriesSection.getKeys(false)) {
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(id);
                if (entrySection != null) {
                    DisplayEntry entry = new DisplayEntry();
                    entry.setId(id);
                    entry.setType(DisplayEntry.Type.HOLOGRAM);
                    entry.setWorld(entrySection.getString("world"));
                    entry.setX(entrySection.getDouble("x"));
                    entry.setY(entrySection.getDouble("y"));
                    entry.setZ(entrySection.getDouble("z"));
                    entry.setYaw((float) entrySection.getDouble("yaw"));
                    entry.setPitch((float) entrySection.getDouble("pitch"));
                    entry.setLines(entrySection.getStringList("lines"));
                    entry.setGlow(entrySection.getBoolean("glow", false));
                    entry.setBillboard(entrySection.getString("billboard", "CENTER"));
                    entry.setScale((float) entrySection.getDouble("scale", 1.0));
                    entry.setActions(entrySection.getStringList("actions"));

                    if (entry.getWorld() != null && entry.getLines() != null && !entry.getLines().isEmpty()) {
                        entries.put(id, entry);
                    }
                }
            }
        }
        logger.info("Carregados " + entries.size() + " holograms do holograms.yml");
    }

    public void save() {
        config = new YamlConfiguration();

        for (Map.Entry<String, DisplayEntry> mapEntry : entries.entrySet()) {
            String id = mapEntry.getKey();
            DisplayEntry entry = mapEntry.getValue();
            String path = "entries." + id;

            config.set(path + ".type", "HOLOGRAM");
            config.set(path + ".world", entry.getWorld());
            config.set(path + ".x", entry.getX());
            config.set(path + ".y", entry.getY());
            config.set(path + ".z", entry.getZ());
            config.set(path + ".yaw", entry.getYaw());
            config.set(path + ".pitch", entry.getPitch());
            config.set(path + ".lines", entry.getLines());
            config.set(path + ".glow", entry.getGlow());
            config.set(path + ".billboard", entry.getBillboard());
            config.set(path + ".scale", entry.getScale());
            config.set(path + ".actions", entry.getActions());
        }

        try {
            config.save(configFile);
            logger.info("Holograms salvos no holograms.yml");
        } catch (IOException e) {
            logger.severe("Erro ao salvar holograms.yml: " + e.getMessage());
        }
    }

    public void addEntry(DisplayEntry entry) {
        if (entry.getId() == null || entry.getId().isEmpty()) {
            logger.warning("Tentativa de salvar uma DisplayEntry (Hologram) sem um ID.");
            return;
        }
        entries.put(entry.getId(), entry);
    }

    public Collection<DisplayEntry> getEntries() {
        return entries.values();
    }

    public DisplayEntry getById(String id) {
        return entries.get(id);
    }

    public boolean removeEntry(String id) {
        if (id == null) return false;
        DisplayEntry removed = entries.remove(id);
        if (removed == null) return false;
        save();
        return true;
    }
}