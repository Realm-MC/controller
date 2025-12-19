package com.palacesky.controller.modules.chat;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.proxy.listeners.StaffChatListener;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class StaffChatModule extends AbstractCoreModule {

    private StaffChatListener staffChatListener;
    private RedisSubscriber redisSubscriber;

    public StaffChatModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "StaffChat";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo que gerencia o chat de staff global via Redis.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Database", "Profile", "ServerManager"};
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    protected void onEnable() throws Exception {
        try {
            this.redisSubscriber = ServiceRegistry.getInstance().requireService(RedisSubscriber.class);
            this.staffChatListener = new StaffChatListener();

            this.redisSubscriber.registerListener(RedisChannel.STAFF_CHAT, this.staffChatListener);
            logger.info("StaffChatListener registrado com sucesso no canal Redis STAFF_CHAT.");

        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Falha ao obter RedisSubscriber! StaffChat não funcionará.", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao registrar StaffChatListener!", e);
        }
    }

    @Override
    protected void onDisable() throws Exception {
        if (this.redisSubscriber != null && this.staffChatListener != null) {
            try {
                this.redisSubscriber.unregisterListener(RedisChannel.STAFF_CHAT);
                logger.info("StaffChatListener removido do canal Redis.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao desregistrar StaffChatListener.", e);
            }
        }
        this.redisSubscriber = null;
        this.staffChatListener = null;
    }
}