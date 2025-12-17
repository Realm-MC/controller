package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.ServerRegistryService;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerInfoRepository;
import com.realmmc.controller.modules.server.data.ServerStatus;
import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.annotations.Listeners;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Listeners
public class BalancerListener {

    private final ServerInfoRepository repository;
    private final ProxyServer proxyServer;

    public BalancerListener() {
        this.repository = new ServerInfoRepository();
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
    }

    @Subscribe
    public void onInitialServerSelect(PlayerChooseInitialServerEvent event) {
        List<ServerInfo> availableLogins = repository.findByTypeAndStatus(ServerType.LOGIN, ServerStatus.ONLINE);

        if (availableLogins.isEmpty()) {
            return;
        }

        Optional<ServerInfo> bestLogin = availableLogins.stream()
                .min(Comparator.comparingInt(ServerInfo::getPlayerCount));

        if (bestLogin.isPresent()) {
            String serverName = bestLogin.get().getName();
            Optional<RegisteredServer> registered = proxyServer.getServer(serverName);

            registered.ifPresent(event::setInitialServer);
        }
    }
}