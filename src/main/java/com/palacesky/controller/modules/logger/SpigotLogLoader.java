package com.palacesky.controller.modules.logger;

import com.palacesky.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

import java.io.File;

public class SpigotLogLoader {

    public static void load(LogService logService) {
        Main plugin = Main.getInstance();
        PluginManager pm = Bukkit.getPluginManager();

        pm.registerEvents(new SpigotLogListener(logService), plugin);
        pm.registerEvents(new CombatLogListener(logService), plugin);
        pm.registerEvents(new InventoryLogListener(logService), plugin);

        plugin.getLogger().info("[LogModule] Listeners de Spigot (Geral, Combate, Inventário) registados.");
    }

    public static File getDataFolder() {
        return Main.getInstance().getDataFolder();
    }

    public static String getServerName() {
        return Bukkit.getServer().getName();
    }
}