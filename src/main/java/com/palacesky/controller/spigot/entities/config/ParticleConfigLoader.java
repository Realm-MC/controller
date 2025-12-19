package com.palacesky.controller.spigot.entities.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ParticleConfigLoader {

    private final JavaPlugin plugin;
    private final File configFile;
    private final Map<String, ParticleEntry> entries = new HashMap<>();

    public ParticleConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "particles.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("particles.yml", false);
            plugin.getLogger().info("Arquivo particles.yml não encontrado, criando um novo.");
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        entries.clear();

        ConfigurationSection section = config.getConfigurationSection("particles");
        if (section == null) {
            plugin.getLogger().info("Nenhuma entrada de partícula encontrada em particles.yml.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection != null) {
                ParticleEntry entry = new ParticleEntry();
                entry.setId(id);
                entry.setWorld(entrySection.getString("world"));
                entry.setX(entrySection.getDouble("x"));
                entry.setY(entrySection.getDouble("y"));
                entry.setZ(entrySection.getDouble("z"));
                entry.setParticleType(entrySection.getString("particleType"));
                entry.setAmount(entrySection.getInt("amount", 1));
                entry.setOffsetX(entrySection.getDouble("offsetX", 0.0));
                entry.setOffsetY(entrySection.getDouble("offsetY", 0.0));
                entry.setOffsetZ(entrySection.getDouble("offsetZ", 0.0));
                entry.setSpeed(entrySection.getDouble("speed", 0.0));
                entry.setParticleData(entrySection.getString("particleData"));
                entry.setLongDistance(entrySection.getBoolean("longDistance", false));
                entry.setUpdateInterval(entrySection.getInt("updateInterval", 20));

                // Carrega os novos dados de animação
                entry.setAnimationType(entrySection.getString("animationType"));
                ConfigurationSection propsSection = entrySection.getConfigurationSection("animationProperties");
                Map<String, String> props = new HashMap<>();
                if (propsSection != null) {
                    for (String key : propsSection.getKeys(false)) {
                        props.put(key, propsSection.getString(key));
                    }
                }
                entry.setAnimationProperties(props);

                entries.put(id, entry);
            }
        }
        plugin.getLogger().info("Carregado " + entries.size() + " entradas de partículas.");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, ParticleEntry> mapEntry : entries.entrySet()) {
            String id = mapEntry.getKey();
            ParticleEntry entry = mapEntry.getValue();
            String path = "particles." + id;

            config.set(path + ".world", entry.getWorld());
            config.set(path + ".x", entry.getX());
            config.set(path + ".y", entry.getY());
            config.set(path + ".z", entry.getZ());
            config.set(path + ".particleType", entry.getParticleType());
            config.set(path + ".amount", entry.getAmount());
            config.set(path + ".offsetX", entry.getOffsetX());
            config.set(path + ".offsetY", entry.getOffsetY());
            config.set(path + ".offsetZ", entry.getOffsetZ());
            config.set(path + ".speed", entry.getSpeed());
            config.set(path + ".particleData", entry.getParticleData());
            config.set(path + ".longDistance", entry.isLongDistance());
            config.set(path + ".updateInterval", entry.getUpdateInterval());

            // Salva os novos dados de animação
            config.set(path + ".animationType", entry.getAnimationType());
            config.set(path + ".animationProperties", entry.getAnimationProperties());
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar particles.yml", e);
        }
    }

    public Collection<ParticleEntry> getEntries() {
        return new ArrayList<>(entries.values());
    }

    public ParticleEntry getById(String id) {
        return entries.get(id);
    }

    public void addEntry(ParticleEntry entry) {
        entries.put(entry.getId(), entry);
    }

    public boolean updateEntry(ParticleEntry updatedEntry) {
        if (entries.containsKey(updatedEntry.getId())) {
            entries.put(updatedEntry.getId(), updatedEntry);
            return true;
        }
        return false;
    }

    public boolean removeEntry(String id) {
        return entries.remove(id) != null;
    }
}