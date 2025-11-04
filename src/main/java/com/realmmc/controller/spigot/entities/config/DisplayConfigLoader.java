package com.realmmc.controller.spigot.entities.config;

import com.realmmc.controller.spigot.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.List;

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
                YamlConfiguration created = new YamlConfiguration();
                created.options().header(String.join("\n",
                        "# RealmMC Controller - Displays",
                        "# Somente sintaxe de actions por labels é suportada.",
                        "# Exemplos:",
                        "#   actions:",
                        "#     - action=message(\"<green>Olá {player}!\"); delay=750ms",
                        "#     - author={player}; action=openmenu(\"loja_principal\")",
                        "#     - action=sound(ENTITY_PLAYER_LEVELUP, 1.0, 1.2)",
                        "#     - action=consolecmd(\"say {player} clicou no {id}\")",
                        "#     - action=teleport(100.5, 65, -30, \"world\"); delay=2s",
                        "#     - action=give(DIAMOND, 2)"
                ));
                created.options().copyHeader(true);
                created.save(configFile);
            } catch (IOException e) {
                logger.severe("Erro ao criar displays.yml: " + e.getMessage());
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
                    String type = entrySection.getString("type", "DISPLAY_ITEM");
                    if (!"DISPLAY_ITEM".equals(type)) {
                        continue;
                    }

                    DisplayEntry entry = new DisplayEntry();
                    entry.setId(id);
                    entry.setType(DisplayEntry.Type.DISPLAY_ITEM);
                    entry.setWorld(entrySection.getString("world"));
                    entry.setX(entrySection.getDouble("x"));
                    entry.setY(entrySection.getDouble("y"));
                    entry.setZ(entrySection.getDouble("z"));
                    entry.setYaw((float) entrySection.getDouble("yaw"));
                    entry.setPitch((float) entrySection.getDouble("pitch"));
                    entry.setItem(entrySection.getString("item"));

                    List<String> lines = entrySection.getStringList("lines");
                    entry.setLines(lines != null ? lines : new ArrayList<>());

                    entry.setGlow(entrySection.getBoolean("glow", false));
                    entry.setBillboard(entrySection.getString("billboard", "CENTER"));
                    entry.setScale((float) entrySection.getDouble("scale", 1.0));

                    List<String> actions = entrySection.getStringList("actions");
                    entry.setActions(actions != null ? actions : new ArrayList<>());

                    entry.setHologramVisible(entrySection.getBoolean("hologramVisible", true));

                    if (entry.getWorld() != null && entry.getItem() != null) {
                        entries.put(id, entry);
                    }
                }
            }
        }
        logger.info("Carregadas " + entries.size() + " entradas do displays.yml");
    }

    public void save() {
        config = new YamlConfiguration();
        config.options().header(String.join("\n",
                "# RealmMC Controller - Displays",
                "# Actions por labels (delay padrão 2s):",
                "#   actions:",
                "#     - action=message(\"<green>Olá {player}!\")",
                "#     - action=openmenu(\"loja_principal\"); delay=1.2s",
                "#     - action=sound(ENTITY_PLAYER_LEVELUP, 1.0, 1.2)",
                "#     - action=teleport(100, 65, -30)",
                "#     - action=give(DIAMOND, 2)"
        ));
        config.options().copyHeader(true);

        for (Map.Entry<String, DisplayEntry> mapEntry : entries.entrySet()) {
            String id = mapEntry.getKey();
            DisplayEntry entry = mapEntry.getValue();
            String path = "entries." + id;

            config.set(path + ".type", "DISPLAY_ITEM");
            config.set(path + ".world", entry.getWorld());
            config.set(path + ".x", entry.getX());
            config.set(path + ".y", entry.getY());
            config.set(path + ".z", entry.getZ());
            config.set(path + ".yaw", entry.getYaw());
            config.set(path + ".pitch", entry.getPitch());
            config.set(path + ".item", entry.getItem());
            config.set(path + ".lines", entry.getLines());
            config.set(path + ".glow", entry.getGlow());
            config.set(path + ".billboard", entry.getBillboard());
            config.set(path + ".scale", entry.getScale());
            config.set(path + ".actions", entry.getActions());
            config.set(path + ".hologramVisible", entry.getHologramVisible());
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
            logger.warning("Tentativa de salvar uma DisplayEntry sem um ID.");
            return;
        }
        entries.put(entry.getId(), entry);
    }

    public void updateEntry(DisplayEntry entry) {
        addEntry(entry);
    }

    public Collection<DisplayEntry> getEntries() {
        return new ArrayList<>(entries.values());
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