package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
// Imports para RoleService (apenas para clearSentWarnings e invalidateSession)
import java.util.List; // Import List (embora não mais usado para notificação)
// Imports Velocity e Geyser
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.plugin.PluginContainer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class PlayerListener {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final RoleService roleService;
    private final StatisticsService statisticsService;
    private final boolean geyserApiAvailable;
    private final Logger logger;

    public PlayerListener() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado!"));
        this.preferencesService = ServiceRegistry.getInstance().getService(PreferencesService.class)
                .orElseThrow(() -> new IllegalStateException("PreferencesService não encontrado!"));
        this.roleService = ServiceRegistry.getInstance().getService(RoleService.class)
                .orElseThrow(() -> new IllegalStateException("RoleService não encontrado para PlayerListener (Velocity)!"));
        this.statisticsService = ServiceRegistry.getInstance().getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não encontrado para PlayerListener (Velocity)!"));

        this.logger = Proxy.getInstance().getLogger();

        Optional<PluginContainer> geyserPlugin = Proxy.getInstance().getServer().getPluginManager().getPlugin("geyser");
        this.geyserApiAvailable = geyserPlugin.isPresent();

        if(geyserApiAvailable) logger.info("Geyser (Velocity) detectado.");
        else logger.info("Geyser (Velocity) não detectado.");
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.NORMAL)
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String displayName = player.getUsername();
        String usernameLower = displayName.toLowerCase();
        long now = System.currentTimeMillis();

        boolean isPremium = Proxy.getInstance().getPremiumLoginStatus().getOrDefault(usernameLower, false);
        UUID finalUuid = isPremium
                ? player.getUniqueId()
                : Proxy.getInstance().getOfflineUuids().getOrDefault(usernameLower, player.getUniqueId());

        String ip = player.getRemoteAddress() instanceof InetSocketAddress isa ? isa.getAddress().getHostAddress() : null;
        String clientVersion = player.getProtocolVersion().getName();
        String clientType = "Java";

        if (geyserApiAvailable) {
            try {
                Optional<GeyserConnection> connectionOpt = Optional.ofNullable(GeyserApi.api().connectionByUuid(player.getUniqueId()));
                if (connectionOpt.isPresent()) {
                    GeyserConnection connection = connectionOpt.get();
                    clientType = "Bedrock";
                    clientVersion = "Bedrock/" + connection.protocolVersion();
                    logger.finer("Jogador Bedrock detectado: " + displayName);
                }
            } catch (Exception | NoClassDefFoundError geyserEx) {
                logger.log(Level.WARNING, "Erro ao acessar Geyser API para " + displayName + ". Assumindo Java.", geyserEx);
                clientType = "Java";
            }
        }

        Proxy.getInstance().getLoginTimestamps().put(finalUuid, System.currentTimeMillis());

        try {
            profileService.ensureProfile(finalUuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro CRÍTICO durante ensureProfile para " + displayName + " (" + finalUuid + ")", e);
            // player.disconnect(Component.text("§cOcorreu um erro ao carregar seu perfil. Tente novamente.")); // Kick comentado
            return;
        }

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);

        // <<< CORREÇÃO: LÓGICA DE NOTIFICAÇÃO DE EXPIRAÇÃO REMOVIDA >>>
        // Limpa avisos antigos (ainda útil para os avisos *antes* de expirar)
        roleService.clearSentWarnings(finalUuid);
        // <<< FIM CORREÇÃO >>>
    }

    @Subscribe(order = com.velocitypowered.api.event.PostOrder.LATE)
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String usernameLower = player.getUsername().toLowerCase();

        UUID finalUuid = profileService.getByUsername(usernameLower)
                .map(Profile::getUuid)
                .orElse(Proxy.getInstance().getOfflineUuids().getOrDefault(usernameLower, player.getUniqueId()));

        Long loginTime = Proxy.getInstance().getLoginTimestamps().remove(finalUuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            if (statisticsService != null && sessionDuration > 0) {
                try {
                    statisticsService.addOnlineTime(finalUuid, sessionDuration);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao salvar tempo online no disconnect para " + finalUuid, e);
                }
            }
        }

        preferencesService.removeCachedLanguage(finalUuid);
        roleService.clearSentWarnings(finalUuid); // Limpa avisos no disconnect

        Proxy.getInstance().getPremiumLoginStatus().remove(usernameLower);
        Proxy.getInstance().getOfflineUuids().remove(usernameLower);

        roleService.invalidateSession(finalUuid);
        logger.finer("Session Cache invalidado para " + finalUuid + " no disconnect.");
    }
}