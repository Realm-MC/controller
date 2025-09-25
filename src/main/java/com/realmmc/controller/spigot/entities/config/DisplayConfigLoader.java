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

public class DisplayConfigLoader {
    private final Logger logger = Main.getInstance().getLogger();
    private final File configFile = new File(Main.getInstance().getDataFolder(), "displays.yml");
    private YamlConfiguration config;
    private final Map<String, DisplayEntry> entries = new HashMap<>();

    public void load() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                logger.info("Arquivo displays.yml criado.");
            } catch (IOException e) {
                logger.severe("Erro ao criar displays.yml: " + e.getMessage());
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        entries.clear();

        ConfigurationSection entriesSection = config.getConfigurationSection("entries");
        if (entriesSection != null) {
            for (String id : entriesSection.getKeys(false)) { // A chave da seção é o ID (String)
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(id);
                if (entrySection != null) {
                    DisplayEntry entry = new DisplayEntry();
                    entry.setId(id);
                    entries.put(id, entry);
                }
            }
        }
        logger.info("Carregadas " + entries.size() + " display items do displays.yml");
    }

    public void save() {
        config = new YamlConfiguration();
        for (Map.Entry<String, DisplayEntry> mapEntry : entries.entrySet()) {
            String id = mapEntry.getKey();
            DisplayEntry entry = mapEntry.getValue();
            String path = "entries." + id;
        }
        try {
            config.save(configFile);
            logger.info("Display items salvos no displays.yml");
        } catch (IOException e) {
            logger.severe("Erro ao salvar displays.yml: " + e.getMessage());
        }
    }

    public void addEntry(DisplayEntry entry) {
        if (entry.getId() == null || entry.getId().isEmpty()) {
            logger.warning("Tentativa de salvar uma DisplayEntry (Item) sem um ID.");
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
}