package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import org.geysermc.geyser.api.GeyserApi;

import java.net.InetSocketAddress;
import java.util.UUID;

@Listeners
public class PlayerListener {

    private final ProfileService profileService = new ProfileService();

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

        String clientType;
        try {
            boolean isBedrock = GeyserApi.api().isBedrockPlayer(player.getUniqueId());
            clientType = isBedrock ? "Bedrock" : "Java";
        } catch (Exception | NoClassDefFoundError e) {
            clientType = "Java";
        }

        Proxy.getInstance().getLoginTimestamps().put(uuid, System.currentTimeMillis());

        profileService.ensureProfile(uuid, username, username, ip, clientVersion, clientType);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(uuid);

        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            ServiceRegistry.getInstance().getService(StatisticsService.class)
                    .ifPresent(statsService -> statsService.addOnlineTime(uuid, sessionDuration));
        }
    }
}