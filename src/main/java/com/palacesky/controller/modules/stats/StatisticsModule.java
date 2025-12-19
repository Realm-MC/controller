package com.palacesky.controller.modules.stats;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.stats.StatisticsService;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class StatisticsModule extends AbstractCoreModule {

    public StatisticsModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Statistics";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo para gestão de estatísticas de jogadores.";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando serviço de estatísticas...");
        StatisticsService statisticsService = new StatisticsService();
        ServiceRegistry.getInstance().registerService(StatisticsService.class, statisticsService);
        logger.info("Serviço de estatísticas inicializado.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().unregisterService(StatisticsService.class);
        logger.info("Serviço de estatísticas finalizado.");
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Profile"};
    }

    @Override
    public int getPriority() {
        return 30;
    }
}