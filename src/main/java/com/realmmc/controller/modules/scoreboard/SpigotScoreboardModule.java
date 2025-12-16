package com.realmmc.controller.modules.scoreboard;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.modules.ModuleManager;
import com.realmmc.controller.core.modules.SystemType;
import com.realmmc.controller.core.services.ServiceRegistry;
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

    @Override
    public String getName() {
        return "SpigotScoreboardModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Scoreboard Padrão (Controller Service)";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SpigotModule", "Profile"};
    }

    @Override
    protected void onEnable() throws Exception {
        if (ModuleManager.getInstance().isClaimed(SystemType.SCOREBOARD)) {
            logger.info("Sistema de SCOREBOARD reivindicado externamente. O módulo padrão não será ativado.");
            return;
        }

        this.scoreboardService = new ScoreboardService();
        ServiceRegistry.getInstance().registerService(ScoreboardService.class, this.scoreboardService);

        Bukkit.getPluginManager().registerEvents(this.scoreboardService, Main.getInstance());

        Bukkit.getOnlinePlayers().forEach(this.scoreboardService::createScoreboard);

        logger.info("SpigotScoreboardModule ativado (Serviço padrão).");
    }

    @Override
    protected void onDisable() throws Exception {
        if (this.scoreboardService != null) {
            HandlerList.unregisterAll(this.scoreboardService);
            this.scoreboardService.shutdown();
            ServiceRegistry.getInstance().unregisterService(ScoreboardService.class);
            this.scoreboardService = null;
            logger.info("SpigotScoreboardModule desativado.");
        }
    }
}