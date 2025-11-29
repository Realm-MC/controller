package com.realmmc.controller.modules.scoreboard;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.services.ScoreboardService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.logging.Logger;

public class SpigotScoreboardModule extends AbstractCoreModule {

    private ScoreboardService scoreboardService;

    public SpigotScoreboardModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "SpigotScoreboardModule"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public String getDescription() { return "Scoreboard Padrão"; }
    @Override public String[] getDependencies() { return new String[]{"SpigotModule"}; }

    @Override
    protected void onEnable() {
        scoreboardService = new ScoreboardService(Main.getInstance());
        Bukkit.getPluginManager().registerEvents(scoreboardService, Main.getInstance());
        logger.info("Scoreboard Padrão ativada.");
    }

    @Override
    protected void onDisable() {
        if (scoreboardService != null) {
            HandlerList.unregisterAll(scoreboardService);
        }
        scoreboardService = null;
    }
}