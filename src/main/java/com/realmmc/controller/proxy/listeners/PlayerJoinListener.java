package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.session.SessionTrackerService;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;


import java.net.InetSocketAddress;
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
    private final boolean geyserApiAvailable;
    private final boolean viaVersionApiAvailable;
    private final Logger logger;
    private final ProxyServer proxyServer;

    public PlayerJoinListener() {
        this.proxyServer = ServiceRegistry.getInstance().getService(ProxyServer.class)
                .orElseThrow(() -> new IllegalStateException("ProxyServer not found in ServiceRegistry!"));
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService not found!"));
        this.preferencesService = ServiceRegistry.getInstance().getService(PreferencesService.class)
                .orElseThrow(() -> new IllegalStateException("PreferencesService not found!"));
        this.roleService = ServiceRegistry.getInstance().getService(RoleService.class)
                .orElseThrow(() -> new IllegalStateException("RoleService not found for PlayerListener (Velocity)!"));
        this.statisticsService = ServiceRegistry.getInstance().getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService not found for PlayerListener (Velocity)!"));
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        this.logger = Proxy.getInstance().getLogger();

        Optional<PluginContainer> geyserPlugin = proxyServer.getPluginManager().getPlugin("geyser");
        this.geyserApiAvailable = geyserPlugin.isPresent();
        this.viaVersionApiAvailable = proxyServer.getPluginManager().isLoaded("viaversion");


        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("[PlayerJoin] SessionTrackerService not found! Session tracking will not work.");
        }
        if(geyserApiAvailable) logger.info("[PlayerJoin] Geyser detected.");
        else logger.info("[PlayerJoin] Geyser not detected.");
        if(viaVersionApiAvailable) logger.info("[PlayerJoin] ViaVersion detected.");
        else logger.info("[PlayerJoin] ViaVersion not detected.");
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.EARLY)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getUniqueId(), event.getUsername()));
            return;
        }
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;
        final UUID uuid = player.getUniqueId();

        roleService.startPreLoadingPlayerData(uuid);
        logger.finer("[PlayerJoin] Role pre-loading started (LoginEvent) for " + player.getUsername());
    }


    @Subscribe(order = com.velocitypowered.api.event.PostOrder.NORMAL)
    public void onPostLogin(PostLoginEvent event) {
        final Player player = event.getPlayer();
        final String displayName = player.getUsername();
        final String usernameLower = displayName.toLowerCase();
        final UUID uuid = player.getUniqueId();

        final String ip = player.getRemoteAddress() instanceof InetSocketAddress isa ? isa.getAddress().getHostAddress() : null;

        final boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(usernameLower, false);

        String tempClientVersion = "Unknown";
        String tempClientType = "Java";
        int tempProtocolVersion = player.getProtocolVersion().getProtocol();

        if (geyserApiAvailable) {
            try {
                Object geyserApi = Class.forName("org.geysermc.geyser.api.GeyserApi").getMethod("api").invoke(null);
                Object connection = geyserApi.getClass().getMethod("connectionByUuid", UUID.class).invoke(geyserApi, uuid);
                if (connection != null) {
                    tempClientType = "Bedrock";
                    tempProtocolVersion = (int) connection.getClass().getMethod("protocolVersion").invoke(connection);
                    tempClientVersion = String.valueOf(tempProtocolVersion);
                    logger.finer("[PlayerJoin] Bedrock player detected: " + displayName);
                }
            } catch (Exception | NoClassDefFoundError geyserEx) {
                logger.log(Level.WARNING, "[PlayerJoin] Error accessing Geyser API for " + displayName + ". Assuming Java.", geyserEx);
                tempClientType = "Java";
            }
        }

        if ("Java".equals(tempClientType)) {
            if (viaVersionApiAvailable) {
                try {
                    tempProtocolVersion = Via.getAPI().getPlayerVersion(uuid);
                    ProtocolVersion pv = ProtocolVersion.getProtocol(tempProtocolVersion);
                    tempClientVersion = (pv != null) ? pv.getName() : String.valueOf(tempProtocolVersion);
                } catch (Exception | NoClassDefFoundError viaEx) {
                    logger.log(Level.FINER, "[PlayerJoin] Failed to get Java version via ViaVersion API for " + displayName + ". Using Velocity fallback.", viaEx);
                    tempClientVersion = player.getProtocolVersion().getName();
                    tempProtocolVersion = player.getProtocolVersion().getProtocol();
                }
            } else {
                tempClientVersion = player.getProtocolVersion().getName();
            }
        }

        final String clientVersion = tempClientVersion;
        final String clientType = tempClientType;
        final int protocolVersion = tempProtocolVersion;

        Proxy.getInstance().getLoginTimestamps().put(uuid, System.currentTimeMillis());

        try {
            final Profile profile = profileService.ensureProfile(uuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);

            final int finalProtocol = protocolVersion;

            String determinedProxyId;
            determinedProxyId = System.getProperty("controller.proxyId");

            if (determinedProxyId == null || determinedProxyId.isEmpty()) {
                determinedProxyId = System.getenv("PROXY_NAME");
            }

            if (determinedProxyId == null || determinedProxyId.isEmpty()) {
                try {
                    determinedProxyId = proxyServer.getBoundAddress().getHostName();
                    logger.warning("Aviso: 'controller.proxyId' (propriedade Java) ou 'PROXY_NAME' (variável de ambiente) não estão definidas! O proxyId está a ser reportado como: " + determinedProxyId);
                } catch (Exception e) {
                    determinedProxyId = "proxy_unknown";
                }
            }

            final String finalProxyId = determinedProxyId;

            sessionTrackerServiceOpt.ifPresent(service -> {
                service.startSession(uuid, profile.getName(), finalProxyId, null, finalProtocol, (int)player.getPing(),
                        ip, clientVersion, clientType, isPremium);

                service.setSessionState(uuid, AuthenticationGuard.STATE_CONNECTING);
                logger.fine("[PlayerJoin] Session state set to CONNECTING for " + displayName);
            });

            preferencesService.loadAndCachePreferences(uuid);

            roleService.clearSentWarnings(uuid);
            roleService.checkAndSendLoginExpirationWarning(player);
            preferencesService.checkAndSendStaffChatWarning(player, uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PlayerJoin] CRITICAL error during ensureProfile/startSession for " + displayName + " (" + uuid + ")", e);
            String translatedKick = Messages.translate(MessageKey.KICK_GENERIC_PROFILE_ERROR);
            player.disconnect(MiniMessage.miniMessage().deserialize(translatedKick));
            Proxy.getInstance().getLoginTimestamps().remove(uuid);
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, displayName));
            roleService.removePreLoginFuture(uuid);
            return;
        }

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;
        final UUID uuid = player.getUniqueId();
        final String username = player.getUsername();

        sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, username));

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(uuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            if (statisticsService != null && sessionDuration > 0) {
                try {
                    statisticsService.addOnlineTime(uuid, sessionDuration);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[PlayerJoin] Error saving online time on disconnect for " + uuid, e);
                }
            }
        }

        preferencesService.removeCachedPreferences(uuid);
        roleService.clearSentWarnings(uuid);

        Proxy.getInstance().getPremiumLoginStatus().remove(player.getUsername().toLowerCase());
        Proxy.getInstance().getOfflineUuids().remove(player.getUsername().toLowerCase());

        roleService.invalidateSession(uuid);
        logger.finer("[PlayerJoin] Session invalidated (local RoleService cache) for " + uuid + " on disconnect.");
    }
}