package com.realmmc.controller.modules.spigot.sounds;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.sounds.SpigotSoundPlayer;

import java.util.logging.Logger;


public class SoundModule extends AbstractCoreModule {


    public SoundModule(Logger logger) {
        super(logger);
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
        return "Framework centralizado e multi-plataforma para gestão e reprodução de sons.";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando SpigotSoundPlayer...");
        SoundPlayer spigotSoundPlayer = new SpigotSoundPlayer();
        ServiceRegistry.getInstance().registerService(SoundPlayer.class, spigotSoundPlayer);
        logger.info("SpigotSoundPlayer registrado com sucesso como SoundPlayer.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().unregisterService(SoundPlayer.class);
        logger.info("SoundPlayer (Spigot) desregistrado.");
    }
}