package com.realmmc.controller.spigot.entities.config;

import com.realmmc.controller.spigot.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NPCConfigLoader {
    private final Logger logger = Main.getInstance().getLogger();
    private final File configFile = new File(Main.getInstance().getDataFolder(), "npcs.yml");
    private YamlConfiguration config;
    private final List<DisplayEntry> entries = new ArrayList<>();
    private int nextId = 1;

    public void load() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                logger.info("Arquivo npcs.yml criado.");
            } catch (IOException e) {
                logger.severe("Erro ao criar npcs.yml: " + e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        entries.clear();

        ConfigurationSection entriesSection = config.getConfigurationSection("entries");
        if (entriesSection != null) {
            for (String key : entriesSection.getKeys(false)) {
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(key);
                if (entrySection != null) {
                    DisplayEntry entry = new DisplayEntry();
                    entry.setId(Integer.parseInt(key));
                    entry.setType(DisplayEntry.Type.NPC);
                    entry.setWorld(entrySection.getString("world"));
                    entry.setX(entrySection.getDouble("x"));
                    entry.setY(entrySection.getDouble("y"));
                    entry.setZ(entrySection.getDouble("z"));
                    entry.setYaw((float) entrySection.getDouble("yaw"));
                    entry.setPitch((float) entrySection.getDouble("pitch"));
                    entry.setMessage(entrySection.getString("name"));
                    entry.setItem(entrySection.getString("skin", "default"));

                    if (entry.getWorld() != null && entry.getMessage() != null) {
                        entries.add(entry);
                        nextId = Math.max(nextId, entry.getId() + 1);
                    }
                }
            }
        }

        logger.info("Carregados " + entries.size() + " NPCs do npcs.yml");
    }

    public void save() {
        config = new YamlConfiguration();

        for (DisplayEntry entry : entries) {
            String path = "entries." + entry.getId();
            config.set(path + ".type", "NPC");
            config.set(path + ".world", entry.getWorld());
            config.set(path + ".x", entry.getX());
            config.set(path + ".y", entry.getY());
            config.set(path + ".z", entry.getZ());
            config.set(path + ".yaw", entry.getYaw());
            config.set(path + ".pitch", entry.getPitch());
            config.set(path + ".name", entry.getMessage());
            config.set(path + ".skin", entry.getItem() != null ? entry.getItem() : "default");
        }

        try {
            config.save(configFile);
            logger.info("NPCs salvos no npcs.yml");
        } catch (IOException e) {
            logger.severe("Erro ao salvar npcs.yml: " + e.getMessage());
        }
    }

    public void addEntry(DisplayEntry entry) {
        entry.setId(nextId++);
        entry.setType(DisplayEntry.Type.NPC);
        entries.add(entry);
    }

    public List<DisplayEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public DisplayEntry getById(int id) {
        return entries.stream()
                .filter(entry -> entry.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public int getNextId() {
        return nextId;
    }
}