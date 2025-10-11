package com.realmmc.controller.modules.commands;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class CommandModule extends AbstractCoreModule {

    private CommandRegistry commandRegistry;

    public CommandModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Command";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo de gerenciamento de comandos compartilhados";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando sistema de comandos...");

        commandRegistry = new CommandRegistry(logger);
        ServiceRegistry.getInstance().registerService(CommandRegistry.class, commandRegistry);

        logger.info("Sistema de comandos inicializado");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("Finalizando sistema de comandos...");

        if (commandRegistry != null) {
            commandRegistry.unregisterAll();
        }

        ServiceRegistry.getInstance().unregisterService(CommandRegistry.class);

        logger.info("Sistema de comandos finalizado");
    }

    @Override
    public int getPriority() {
        return 40;
    }
}