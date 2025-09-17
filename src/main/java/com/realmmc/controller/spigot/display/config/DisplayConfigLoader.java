package com.realmmc.controller.spigot.display.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DisplayConfigLoader {
    private final Plugin plugin;
    private final List<DisplayEntry> entries = new ArrayList<>();

    public DisplayConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        entries.clear();
        File file = new File(plugin.getDataFolder(), "displays.yml");
        if (!file.exists()) {
            plugin.saveResource("displays.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = cfg.getMapList("entries");
        if (list == null) return;
        for (Map<?, ?> map : list) {
            int id = toInt(map.get("id"), -1);
            String typeStr = toStr(map.get("type"));
            String item = toStr(map.get("item"));
            String actionStr = toStr(map.get("action"));
            String message = toStr(map.get("message"));
            DisplayEntry.Type type = DisplayEntry.Type.fromString(typeStr);
            DisplayEntry.Action action = DisplayEntry.Action.fromString(actionStr);
            if (id >= 0 && type != null) {
                entries.add(DisplayEntry.builder()
                        .id(id)
                        .type(type)
                        .item(item)
                        .action(action)
                        .message(message)
                        .build());
            }
        }
    }

    public List<DisplayEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public DisplayEntry getById(int id) {
        for (DisplayEntry e : entries) if (e.getId() == id) return e;
        return null;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
