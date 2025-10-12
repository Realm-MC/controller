package com.realmmc.controller.modules.group;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class GroupCommandModule extends AbstractCoreModule {

    public GroupCommandModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() { return "GroupCommand"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "Adiciona o comando /group para gestão de cargos e permissões."; }

    @Override
    public String[] getDependencies() { return new String[]{"Permission", "PlayerDisplay"}; }

    @Override
    public int getPriority() { return 100; } // Carrega depois dos serviços que usa

    @Override
    protected void onEnable() throws Exception {} // O CommandManager faz o trabalho por nós

    @Override
    protected void onDisable() throws Exception {} // O CommandManager faz o trabalho por nós
}