package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class SpigotPlayerListener implements Listener {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final StatisticsService statisticsService;
    private final ConcurrentHashMap<UUID, Long> loginTimestamps = new ConcurrentHashMap<>();
    private final boolean geyserApiAvailable;
    private final boolean viaVersionApiAvailable;
    private final Logger logger;

    public SpigotPlayerListener() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado para SpigotPlayerListener!"));
        this.preferencesService = ServiceRegistry.getInstance().getService(PreferencesService.class)
                .orElseThrow(() -> new IllegalStateException("PreferencesService não encontrado para SpigotPlayerListener!"));
        this.statisticsService = ServiceRegistry.getInstance().getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não encontrado para SpigotPlayerListener!"));
        this.logger = Main.getInstance().getLogger();

        this.geyserApiAvailable = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");

        if (geyserApiAvailable) {
            logger.info("Geyser-Spigot detectado.");
        } else {
            logger.info("Geyser-Spigot não detectado.");
        }
        if (viaVersionApiAvailable) {
            logger.info("ViaVersion detectado.");
        } else {
            logger.info("ViaVersion não detectado. A versão do cliente Java pode não ser precisa.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String displayName = player.getName();
        String usernameLower = displayName.toLowerCase();

        boolean isPremium = profileService.getByUuid(uuid).map(Profile::isPremiumAccount).orElse(false);

        String ip = null;
        InetSocketAddress address = player.getAddress();
        if (address != null) {
            InetAddress inetAddress = address.getAddress();
            if (inetAddress != null) {
                ip = inetAddress.getHostAddress();
            }
        }

        String clientVersion = "Unknown";
        String clientType = "Java";

        if (geyserApiAvailable) {
            try {
                GeyserApi geyserApi = GeyserApi.api();
                GeyserConnection connection = geyserApi.connectionByUuid(uuid);
                if (connection != null) {
                    clientType = "Bedrock";
                    int bedrockProtocol = connection.protocolVersion();
                    clientVersion = "Bedrock/" + bedrockProtocol;
                    logger.fine("Jogador Bedrock detectado: " + displayName + " (Protocolo: " + bedrockProtocol + ")");
                } else {
                    clientType = "Java";
                    if (viaVersionApiAvailable) {
                        try {
                            int protocolVersion = Via.getAPI().getPlayerVersion(uuid);
                            ProtocolVersion pv = ProtocolVersion.getProtocol(protocolVersion);
                            clientVersion = (pv != null) ? pv.getName() : "Java/" + protocolVersion;
                        } catch (Exception | NoClassDefFoundError viaEx) {
                            logger.log(Level.WARNING, "Falha ao obter versão Java via ViaVersion API para " + displayName, viaEx);
                            clientVersion = "Java";
                        }
                    } else {
                        clientVersion = "Unknown";
                    }
                }
            } catch (Exception | NoClassDefFoundError geyserEx) {
                logger.log(Level.SEVERE, "Erro crítico ao acessar Geyser API para " + displayName + ". Assumindo Java.", geyserEx);
                clientType = "Java";
                clientVersion = "Java";
            }
        } else {
            clientType = "Java";
            if (viaVersionApiAvailable) {
                try {
                    int protocolVersion = Via.getAPI().getPlayerVersion(uuid);
                    ProtocolVersion pv = ProtocolVersion.getProtocol(protocolVersion);
                    clientVersion = (pv != null) ? pv.getName() : "Java/" + protocolVersion;
                } catch (Exception | NoClassDefFoundError viaEx) {
                    logger.log(Level.WARNING, "Falha ao obter versão Java via ViaVersion API para " + displayName, viaEx);
                    clientVersion = "Java";
                }
            } else {
                clientVersion = "Unknown";
            }
        }


        loginTimestamps.put(uuid, System.currentTimeMillis());

        profileService.ensureProfile(uuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Long loginTime = loginTimestamps.remove(uuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            if (sessionDuration > 0) {
                statisticsService.addOnlineTime(uuid, sessionDuration);
            }
        }

        preferencesService.removeCachedLanguage(uuid);
    }
}