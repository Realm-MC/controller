package com.palacesky.controller.modules.motd;

import com.github.retrooper.packetevents.PacketEvents;
import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.proxy.listeners.MotdChatListener;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class MotdModule extends AbstractCoreModule {

    private MotdService motdService;
    private MotdChatListener chatListener;

    public MotdModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "MotdModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Gerencia o MOTD do Proxy com chat interativo seguro.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Database", "Command"};
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[MotdModule] Inicializando serviço de MOTD...");

        this.motdService = new MotdService(logger);
        ServiceRegistry.getInstance().registerService(MotdService.class, this.motdService);

        this.chatListener = new MotdChatListener();
        PacketEvents.getAPI().getEventManager().registerListener(this.chatListener);
        logger.info("[MotdModule] PacketListener para chat registrado com prioridade LOWEST.");

        logger.info("[MotdModule] Módulo ativado com sucesso.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.unregisterListener(RedisChannel.CONTROLLER_BROADCAST));

        if (this.chatListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this.chatListener);
        }

        ServiceRegistry.getInstance().unregisterService(MotdService.class);
        logger.info("[MotdModule] Módulo desativado.");
    }
}