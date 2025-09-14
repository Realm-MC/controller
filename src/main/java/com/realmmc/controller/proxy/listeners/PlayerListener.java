package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.profile.ProfileService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

@Listeners
public class PlayerListener {

    private final ProfileService profiles = new ProfileService();

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String ip = null;
        if (player.getRemoteAddress() instanceof InetSocketAddress isa) {
            ip = isa.getAddress() != null ? isa.getAddress().getHostAddress() : null;
        }
        String clientVersion = player.getProtocolVersion() != null ? player.getProtocolVersion().getName() : null;
        String clientType = "Java";

        profiles.ensureProfile(uuid, username, username, ip, clientVersion, clientType);
        profiles.recordLogin(uuid, ip, clientVersion, clientType, username);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // TODO: Espaço para futuras métricas; nada crítico no momento
    }
}
