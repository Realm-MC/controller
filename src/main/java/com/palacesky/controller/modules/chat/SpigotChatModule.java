package com.palacesky.controller.modules.chat;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.spigot.Main;
import com.palacesky.controller.spigot.services.ChatService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.logging.Logger;

public class SpigotChatModule extends AbstractCoreModule {

    private ChatService chatService;

    public SpigotChatModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "SpigotChatModule";
    }

    @Override
    public String getVersion() {
        return "1.1";
    }

    @Override
    public String getDescription() {
        return "Formatação de Chat Padrão (Controller)";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SpigotModule"};
    }

    @Override
    protected void onEnable() {
        chatService = new ChatService();
        Bukkit.getPluginManager().registerEvents(chatService, Main.getInstance());
        logger.info("SpigotChatModule ativado (Chat Padrão).");
    }

    @Override
    protected void onDisable() {
        if (chatService != null) {
            HandlerList.unregisterAll(chatService);
            logger.info("SpigotChatModule desativado.");
        }
        chatService = null;
    }
}