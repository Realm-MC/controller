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

public class NPCConfigLoader {
    private final Logger logger = Main.getInstance().getLogger();
    private final File configFile = new File(Main.getInstance().getDataFolder(), "npcs.yml");
    private YamlConfiguration config;
    private final Map<String, DisplayEntry> entries = new HashMap<>();

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
            for (String id : entriesSection.getKeys(false)) {
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(id);
                if (entrySection != null) {
                    DisplayEntry entry = new DisplayEntry();
                    entry.setId(id);
                    entry.setType(DisplayEntry.Type.NPC);
                    entry.setWorld(entrySection.getString("world"));
                    entry.setX(entrySection.getDouble("x"));
                    entry.setY(entrySection.getDouble("y"));
                    entry.setZ(entrySection.getDouble("z"));
                    entry.setYaw((float) entrySection.getDouble("yaw"));
                    entry.setPitch((float) entrySection.getDouble("pitch"));
                    entry.setMessage(entrySection.getString("name"));
                    entry.setItem(entrySection.getString("skin", "default"));
                    entry.setTexturesValue(entrySection.getString("textures.value"));
                    entry.setTexturesSignature(entrySection.getString("textures.signature"));
                    entry.setActions(entrySection.getStringList("actions"));

                    if (entry.getWorld() != null && entry.getMessage() != null) {
                        entries.put(id, entry);
                    }
                }
            }
        }
        logger.info("Carregados " + entries.size() + " NPCs do npcs.yml");
    }

    public void save() {
        config = new YamlConfiguration();

        for (Map.Entry<String, DisplayEntry> mapEntry : entries.entrySet()) {
            String id = mapEntry.getKey();
            DisplayEntry entry = mapEntry.getValue();
            String path = "entries." + id;

            config.set(path + ".type", "NPC");
            config.set(path + ".world", entry.getWorld());
            config.set(path + ".x", entry.getX());
            config.set(path + ".y", entry.getY());
            config.set(path + ".z", entry.getZ());
            config.set(path + ".yaw", entry.getYaw());
            config.set(path + ".pitch", entry.getPitch());
            config.set(path + ".name", entry.getMessage());
            config.set(path + ".skin", entry.getItem() != null ? entry.getItem() : "default");
            if (entry.getTexturesValue() != null && entry.getTexturesSignature() != null) {
                config.set(path + ".textures.value", entry.getTexturesValue());
                config.set(path + ".textures.signature", entry.getTexturesSignature());
            }
            config.set(path + ".actions", entry.getActions());
        }

        try {
            config.save(configFile);
            logger.info("NPCs salvos no npcs.yml");
        } catch (IOException e) {
            logger.severe("Erro ao salvar npcs.yml: " + e.getMessage());
        }
    }

    public void addEntry(DisplayEntry entry) {
        if (entry.getId() == null || entry.getId().isEmpty()) {
            logger.warning("Tentativa de salvar uma DisplayEntry sem um ID.");
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

    public void clearEntries() {
        entries.clear();
    }
}