package com.palacesky.controller.modules.tablist;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.spigot.Main;
import com.palacesky.controller.spigot.services.TablistService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.logging.Logger;

public class SpigotTablistModule extends AbstractCoreModule {

    private TablistService tablistService;

    public SpigotTablistModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "SpigotTablistModule"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public String getDescription() { return "Tablist Padrão Animada"; }

    @Override
    protected void onEnable() {
        tablistService = new TablistService(Main.getInstance());
        Bukkit.getPluginManager().registerEvents(tablistService, Main.getInstance());
        logger.info("Tablist Padrão ativada.");
    }

    @Override
    protected void onDisable() {
        if (tablistService != null) {
            HandlerList.unregisterAll(tablistService);
        }
        tablistService = null;
    }
}