package com.palacesky.controller.modules.logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import java.net.InetSocketAddress;

public class ProxyLogListener {

    private final LogService logService;

    public ProxyLogListener(LogService logService) {
        this.logService = logService;
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player p = event.getPlayer();
        InetSocketAddress ip = p.getRemoteAddress();
        logService.log("PROXY-AUTH", p.getUsername() + " conectou na rede. IP: " + ip.getHostString());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        logService.log("PROXY-QUIT", event.getPlayer().getUsername() + " desconectou da rede.");
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        logService.log("PROXY-SWITCH", event.getPlayer().getUsername() + " conectou ao servidor: " + serverName);
    }
}