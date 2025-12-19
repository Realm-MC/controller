package com.palacesky.controller.modules.particle;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.spigot.Main;
import com.palacesky.controller.spigot.entities.particles.ParticleService;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class ParticleModule extends AbstractCoreModule {

    public ParticleModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Particle";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo para gestão de efeitos de partículas estáticos e animados.";
    }

    @Override
    public int getPriority() {
        return 45;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando ParticleService...");
        ParticleService particleService = new ParticleService(Main.getInstance());
        ServiceRegistry.getInstance().registerService(ParticleService.class, particleService);
        logger.info("ParticleService registado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        logger.info("Finalizando ParticleService...");
        ServiceRegistry.getInstance().getService(ParticleService.class).ifPresent(ParticleService::stopAllParticles);
        ServiceRegistry.getInstance().unregisterService(ParticleService.class);
        logger.info("ParticleService finalizado com sucesso.");
    }
}