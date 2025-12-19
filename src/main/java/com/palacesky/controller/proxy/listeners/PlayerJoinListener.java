package com.palacesky.controller.proxy.listeners;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.cosmetics.CosmeticsService;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.preferences.PreferencesService;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.session.SessionTrackerService;
import com.palacesky.controller.shared.stats.StatisticsService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class PlayerJoinListener {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final RoleService roleService;
    private final StatisticsService statisticsService;
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;
    private final Logger logger;

    public PlayerJoinListener() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.statisticsService = ServiceRegistry.getInstance().requireService(StatisticsService.class);
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        this.logger = Proxy.getInstance().getLogger();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getUniqueId(), event.getUsername()));
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        roleService.startPreLoadingPlayerData(uuid);

        sessionTrackerServiceOpt.ifPresent(service -> {
            String proxyId = System.getProperty("controller.proxyId", System.getenv("PROXY_NAME"));
            if (proxyId == null) proxyId = "proxy_unknown";

            String ip = player.getRemoteAddress().getAddress().getHostAddress();
            int protocol = player.getProtocolVersion().getProtocol();
            boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(username.toLowerCase(), false);

            service.startSession(uuid, username, proxyId, null, protocol, -1, ip, null, null, isPremium, null);
        });

        logger.info("[PlayerJoin] Sessão iniciada (CONNECTING) para " + username);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String username = player.getUsername();
        final String ip = player.getRemoteAddress().getAddress().getHostAddress();

        Proxy.getInstance().getLoginTimestamps().put(uuid, System.currentTimeMillis());

        try {
            boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(username.toLowerCase(), false);
            String clientVersion = player.getProtocolVersion().getName();

            Profile profile = profileService.ensureProfile(uuid, username, username.toLowerCase(), ip, clientVersion, "Java", isPremium, player);

            ServiceRegistry.getInstance().getService(CosmeticsService.class).ifPresent(cs -> {
                cs.ensureCosmetics(profile);
            });

            sessionTrackerServiceOpt.ifPresent(service -> {
                service.setSessionField(uuid, "username", profile.getName());
                service.setSessionField(uuid, "clientVersion", clientVersion);
                service.setSessionField(uuid, "role", profile.getPrimaryRoleName());
                service.setSessionField(uuid, "cash", String.valueOf(profile.getCash()));
                service.setSessionField(uuid, "medal", profile.getEquippedMedal());
                service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE);
            });

            preferencesService.loadAndCachePreferences(uuid);

            roleService.clearSentWarnings(uuid);

            roleService.checkAndSendLoginExpirationWarning(player);

            Proxy.getInstance().getPremiumLoginStatus().remove(username.toLowerCase());
            Proxy.getInstance().getOfflineUuids().remove(username.toLowerCase());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PlayerJoin] Erro crítico no PostLogin para " + username, e);
            player.disconnect(MiniMessage.miniMessage().deserialize(Messages.translate(MessageKey.KICK_GENERIC_PROFILE_ERROR)));
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, player.getUsername()));
        logger.info("[PlayerJoin] Sessão finalizada para " + player.getUsername());

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(uuid);
        if (loginTime != null) {
            long duration = System.currentTimeMillis() - loginTime;
            if (duration > 0) {
                try { statisticsService.addOnlineTime(uuid, duration); } catch (Exception ignored) {}
            }
        }

        preferencesService.removeCachedPreferences(uuid);
        roleService.invalidateSession(uuid);
        roleService.clearSentWarnings(uuid);
    }
}