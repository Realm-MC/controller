package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import org.geysermc.geyser.api.GeyserApi;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

@Listeners
public class PlayerListener {

    private final ProfileService profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
            .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado!"));
    private final PreferencesService preferencesService = ServiceRegistry.getInstance().getService(PreferencesService.class)
            .orElseThrow(() -> new IllegalStateException("PreferencesService não encontrado!"));


    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String displayName = player.getUsername();
        String usernameLower = displayName.toLowerCase();
        boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(usernameLower, false);
        UUID finalUuid = isPremium ? player.getUniqueId() : Proxy.getInstance().getOfflineUuids().getOrDefault(usernameLower, player.getUniqueId());
        String ip = player.getRemoteAddress() instanceof InetSocketAddress isa ? isa.getAddress().getHostAddress() : null;
        String clientVersion = player.getProtocolVersion().getName();
        String clientType;
        try {
            boolean isBedrock = GeyserApi.api().isBedrockPlayer(player.getUniqueId());
            clientType = isBedrock ? "Bedrock" : "Java";
        } catch (Throwable e) { clientType = "Java"; }

        Proxy.getInstance().getLoginTimestamps().put(finalUuid, System.currentTimeMillis());

        profileService.ensureProfile(finalUuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String usernameLower = player.getUsername().toLowerCase();
        UUID finalUuid = profileService.getByUsername(usernameLower)
                .map(Profile::getUuid)
                .orElse(player.getUniqueId());

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(finalUuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            Optional<StatisticsService> statsOpt = ServiceRegistry.getInstance().getService(StatisticsService.class);
            statsOpt.ifPresent(statsService -> {
                if(sessionDuration > 0) {
                    statsService.addOnlineTime(finalUuid, sessionDuration);
                }
            });
        }

        preferencesService.removeCachedLanguage(finalUuid);

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);
    }
}