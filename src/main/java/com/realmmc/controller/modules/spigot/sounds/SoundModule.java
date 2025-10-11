package com.realmmc.controller.modules.spigot.sounds;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.sounds.SoundService;

import java.util.logging.Logger;

public class SoundModule extends AbstractCoreModule {

    private final Main plugin;

    public SoundModule(Main plugin, Logger logger) {
        super(logger);
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Sound";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Framework centralizado para gestão e reprodução de sons.";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando SoundService...");
        SoundService soundService = new SoundService(plugin);
        ServiceRegistry.getInstance().registerService(SoundService.class, soundService);
        logger.info("SoundService registado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().unregisterService(SoundService.class);
        logger.info("SoundService desregistado.");
    }
}