package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.profile.Profile;
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

    private final ProfileService profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
            .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado!"));

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        String usernameLower = username.toLowerCase();

        boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(usernameLower, false);

        UUID finalUuid = isPremium
                ? player.getUniqueId()
                : Proxy.getInstance().getOfflineUuids().getOrDefault(usernameLower, player.getUniqueId());

        String ip = player.getRemoteAddress() instanceof InetSocketAddress isa ? isa.getAddress().getHostAddress() : null;
        String clientVersion = player.getProtocolVersion().getName();

        String clientType;
        try {
            boolean isBedrock = GeyserApi.api().isBedrockPlayer(player.getUniqueId());
            clientType = isBedrock ? "Bedrock" : "Java";
        } catch (Throwable e) {
            clientType = "Java";
        }

        Proxy.getInstance().getLoginTimestamps().put(finalUuid, System.currentTimeMillis());
        profileService.ensureProfile(finalUuid, username, username, ip, clientVersion, clientType, isPremium);

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        UUID finalUuid = profileService.getByUsername(player.getUsername())
                .map(Profile::getUuid)
                .orElse(player.getUniqueId());

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(finalUuid);

        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            ServiceRegistry.getInstance().getService(StatisticsService.class)
                    .ifPresent(statsService -> statsService.addOnlineTime(finalUuid, sessionDuration));
        }

        String usernameLower = player.getUsername().toLowerCase();
        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);
    }
}