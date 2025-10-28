package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
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

// Imports para RoleService (apenas para clearSentWarnings)
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

// Imports para ViaVersion e Geyser
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
    private final RoleService roleService; // Adicionado RoleService
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
        this.roleService = ServiceRegistry.getInstance().getService(RoleService.class)
                .orElseThrow(() -> new IllegalStateException("RoleService não encontrado para SpigotPlayerListener!"));
        this.logger = Main.getInstance().getLogger();

        this.geyserApiAvailable = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");

        if (geyserApiAvailable) logger.info("Geyser-Spigot detectado."); else logger.info("Geyser-Spigot não detectado.");
        if (viaVersionApiAvailable) logger.info("ViaVersion detectado."); else logger.info("ViaVersion não detectado.");
    }

    @EventHandler(priority = EventPriority.LOWEST) // Roda cedo para garantir o perfil
    public void onPlayerJoinEnsureProfile(PlayerJoinEvent event) {
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

        // Lógica Geyser/ViaVersion
        if (geyserApiAvailable) {
            try {
                GeyserApi geyserApi = GeyserApi.api();
                GeyserConnection connection = geyserApi.connectionByUuid(uuid);
                if (connection != null) {
                    clientType = "Bedrock";
                    clientVersion = connection.javaUsername() + " (Bedrock/" + connection.protocolVersion() + ")";
                    logger.finer("Jogador Bedrock detectado: " + displayName);
                }
            } catch (Exception | NoClassDefFoundError geyserEx) {
                logger.log(Level.WARNING, "Erro ao acessar Geyser API para " + displayName + ". Assumindo Java.", geyserEx);
            }
        }

        if ("Java".equals(clientType)) {
            if (viaVersionApiAvailable) {
                try {
                    int protocolVersion = Via.getAPI().getPlayerVersion(uuid);
                    ProtocolVersion pv = ProtocolVersion.getProtocol(protocolVersion);
                    clientVersion = (pv != null) ? pv.getName() : "Java/" + protocolVersion;
                } catch (Exception | NoClassDefFoundError viaEx) {
                    logger.log(Level.FINER, "Falha ao obter versão Java via ViaVersion API para " + displayName + ". Usando fallback.", viaEx);
                    clientVersion = "Java";
                }
            } else {
                clientVersion = "Java (ViaVersion Ausente)";
            }
        }

        loginTimestamps.put(uuid, System.currentTimeMillis());

        try {
            profileService.ensureProfile(uuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro CRÍTICO durante ensureProfile para " + displayName + " (" + uuid + ")", e);
            player.kickPlayer("§cOcorreu um erro ao carregar seu perfil. Tente novamente."); // Kick em caso de falha grave
            return; // Retorna para não executar o resto
        }

        // <<< CORREÇÃO: Movido clearSentWarnings para cá >>>
        // Limpa avisos de expiração antigos (caso tenha recebido antes de deslogar)
        roleService.clearSentWarnings(uuid);
    }

    // <<< CORREÇÃO: MÉTODO onPlayerJoinNotify REMOVIDO >>>
    // A lógica de notificação de expiração no login foi removida conforme solicitado.


    @EventHandler(priority = EventPriority.MONITOR) // Roda por último
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Long loginTime = loginTimestamps.remove(uuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            if (sessionDuration > 0) {
                try {
                    statisticsService.addOnlineTime(uuid, sessionDuration);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao salvar tempo online no quit para " + uuid, e);
                }
            }
        }

        preferencesService.removeCachedLanguage(uuid);
        roleService.clearSentWarnings(uuid); // Limpa avisos no quit
    }
}