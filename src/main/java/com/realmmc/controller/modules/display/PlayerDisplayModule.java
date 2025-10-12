package com.realmmc.controller.modules.display;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.display.PlayerDisplayService;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class PlayerDisplayModule extends AbstractCoreModule {

    public PlayerDisplayModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "PlayerDisplay";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Serviço para formatar e exibir nomes de jogadores.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Permission", "Profile"};
    }

    @Override
    public int getPriority() {
        return 35; // Carrega depois dos serviços de dados
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando PlayerDisplayService...");
        PlayerDisplayService displayService = new PlayerDisplayService();
        ServiceRegistry.getInstance().registerService(PlayerDisplayService.class, displayService);
        logger.info("PlayerDisplayService registrado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().unregisterService(PlayerDisplayService.class);
    }
}