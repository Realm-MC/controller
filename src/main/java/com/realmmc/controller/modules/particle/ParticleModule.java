package com.realmmc.controller.modules.particle;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.particles.ParticleService;

import java.util.logging.Logger;

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
        // Carrega depois dos serviços base, mas antes de módulos que possam usá-lo.
        return 45;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando ParticleService...");
        // Usamos Main.getInstance() para passar a instância do plugin para o serviço
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