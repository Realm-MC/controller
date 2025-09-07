package com.realmmc.controller.proxy;

import com.realmmc.controller.proxy.commands.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(id = "controller", name = "Controller", description = "API controller to realmmc", version = "1.0.0", authors = {"onyell", "lucas"})
public class Proxy {

    @Getter
    private static Proxy instance;
    @Getter
    private final ProxyServer server;
    @Getter
    private final Logger logger;
    private final Path directory;

    @Inject
    public Proxy(ProxyServer server, Logger logger, @DataDirectory Path directory) {
        this.server = server;
        this.logger = logger;
        this.directory = directory;
        instance = this;
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        CommandManager.registerAll(this);
        logger.info("Controller enabled!" + server.getPluginManager().getPlugin("controller").get().getDescription().getVersion());
    }

    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        logger.info("Controller disabled!" + server.getPluginManager().getPlugin("controller").get().getDescription().getVersion());
    }
}
