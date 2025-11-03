package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.stats.StatisticsService;
import com.realmmc.controller.shared.session.SessionTrackerService;
import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

// Imports Geyser/ViaVersion/ProtocolSupport
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;

@Listeners
public class SpigotPlayerListener implements Listener {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final StatisticsService statisticsService;
    private final RoleService roleService;
    private final Optional<SessionTrackerService> sessionTrackerServiceOpt;
    private final boolean geyserApiAvailable;
    private final boolean viaVersionApiAvailable;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, Long> loginTimestamps = new ConcurrentHashMap<>();

    public SpigotPlayerListener() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado para SpigotPlayerListener!"));
        this.preferencesService = ServiceRegistry.getInstance().getService(PreferencesService.class)
                .orElseThrow(() -> new IllegalStateException("PreferencesService não encontrado para SpigotPlayerListener!"));
        this.statisticsService = ServiceRegistry.getInstance().getService(StatisticsService.class)
                .orElseThrow(() -> new IllegalStateException("StatisticsService não encontrado para SpigotPlayerListener!"));
        this.roleService = ServiceRegistry.getInstance().getService(RoleService.class)
                .orElseThrow(() -> new IllegalStateException("RoleService não encontrado para SpigotPlayerListener!"));
        this.sessionTrackerServiceOpt = ServiceRegistry.getInstance().getService(SessionTrackerService.class);
        this.logger = Main.getInstance().getLogger();
        this.geyserApiAvailable = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
        this.viaVersionApiAvailable = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");

        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("SessionTrackerService não encontrado! Rastreamento de sessão não funcionará.");
        }
        if (geyserApiAvailable) logger.info("Geyser-Spigot detectado."); else logger.info("Geyser-Spigot não detectado.");
        if (viaVersionApiAvailable) logger.info("ViaVersion detectado."); else logger.info("ViaVersion não detectado.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLoginEnsureProfile(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getPlayer().getUniqueId(), event.getPlayer().getName())); // Passa username
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String displayName = player.getName();
        String usernameLower = displayName.toLowerCase();

        sessionTrackerServiceOpt.ifPresent(service ->
                service.setSessionState(uuid, AuthenticationGuard.STATE_CONNECTING)
        );

        String ip = null;
        try { ip = event.getAddress().getHostAddress(); }
        catch (Exception e) { logger.log(Level.FINER, "Não foi possível obter IP do PlayerLoginEvent para " + displayName, e); }
        if (ip == null) {
            try {
                InetSocketAddress address = player.getAddress();
                if (address != null) {
                    InetAddress inetAddressFallback = address.getAddress();
                    if (inetAddressFallback != null) { ip = inetAddressFallback.getHostAddress(); }
                }
            } catch (Exception ignored) {}
        }

        boolean isPremium = profileService.getByUuid(uuid).map(Profile::isPremiumAccount).orElse(false);

        String clientVersion = "Unknown";
        String clientType = "Java";
        int protocolVersion = -1;

        if (geyserApiAvailable) {
            try {
                Object geyserApi = Class.forName("org.geysermc.geyser.api.GeyserApi").getMethod("api").invoke(null);
                Object connection = geyserApi.getClass().getMethod("connectionByUuid", UUID.class).invoke(geyserApi, uuid);
                if (connection != null) {
                    clientType = "Bedrock";
                    String javaUsername = (String) connection.getClass().getMethod("javaUsername").invoke(connection);
                    int bedrockProtocol = (int) connection.getClass().getMethod("protocolVersion").invoke(connection);
                    clientVersion = javaUsername + " (Bedrock/" + bedrockProtocol + ")";
                    protocolVersion = bedrockProtocol;
                    logger.finer("Jogador Bedrock detectado: " + displayName);
                }
            } catch (Exception | NoClassDefFoundError geyserEx) {
                logger.log(Level.WARNING, "Erro ao acessar Geyser API para " + displayName + ". Assumindo Java.", geyserEx);
                clientType = "Java";
            }
        }

        if ("Java".equals(clientType)) {
            if (viaVersionApiAvailable) {
                try {
                    protocolVersion = Via.getAPI().getPlayerVersion(uuid);
                    ProtocolVersion pv = ProtocolVersion.getProtocol(protocolVersion);
                    clientVersion = (pv != null) ? pv.getName() : "Java/" + protocolVersion;
                } catch (Exception | NoClassDefFoundError viaEx) {
                    logger.log(Level.FINER, "Falha ao obter versão Java via ViaVersion API para " + displayName + ". Usando fallback.", viaEx);
                    clientVersion = "Java";
                    protocolVersion = -1;
                }
            } else {
                clientVersion = "Java (ViaVersion Ausente)";
            }
            if(protocolVersion == -1){
                try {
                    Class<?> protocolSupportApi = Class.forName("protocolsupport.api.ProtocolSupportAPI");
                    Object apiInstance = protocolSupportApi.getMethod("getAPI").invoke(null);
                    protocolVersion = (int) apiInstance.getClass().getMethod("getProtocolVersion", Player.class).invoke(apiInstance, player);
                } catch (Exception | NoClassDefFoundError ignored) {
                    try {
                        Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
                        Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
                        Object networkManager = connection.getClass().getField("networkManager").get(connection);
                        try {
                            protocolVersion = (int) networkManager.getClass().getMethod("getVersion").invoke(networkManager);
                        } catch (NoSuchMethodException ex) {
                            protocolVersion = (int) networkManager.getClass().getMethod("getProtocolVersion").invoke(networkManager);
                        }
                    } catch (Exception | NoClassDefFoundError nmsEx) {
                        logger.log(Level.FINEST, "Não foi possível obter a versão do protocolo NMS para " + displayName, nmsEx);
                        protocolVersion = -1;
                    }
                }
            }
        }

        loginTimestamps.put(uuid, System.currentTimeMillis());

        try {
            Profile profile = profileService.ensureProfile(uuid, displayName, usernameLower, ip, clientVersion, clientType, isPremium, player);

            final int finalProtocolForLambda = protocolVersion;
            final String serverName = Bukkit.getServer().getName();
            final String proxyIdPlaceholder = "spigot_direct";

            sessionTrackerServiceOpt.ifPresent(service ->
                    service.startSession(uuid, profile.getName(), proxyIdPlaceholder, serverName, finalProtocolForLambda, -1)
            );

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro CRÍTICO durante ensureProfile/startSession para " + displayName + " (" + uuid + ")", e);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cOcorreu um erro ao carregar/registrar sua sessão. Tente novamente.");
            loginTimestamps.remove(uuid);
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, displayName)); // Passa username
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinFinalize(PlayerJoinEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        this.roleService.clearSentWarnings(uuid);

        sessionTrackerServiceOpt.ifPresent(service -> {
            try {
                int currentPing = player.getPing();
                service.setSessionField(uuid, "ping", String.valueOf(currentPing));
            } catch (Exception e) {
                logger.log(Level.FINEST, "Não foi possível atualizar ping inicial para " + player.getName(), e);
            }
        });
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getName();

        // 1. Limpa a sessão no Redis (previne o log de erro se for a primeira limpeza)
        sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, username));

        // 2. Invalida o cache de sessão local do RoleService (crucial para o próximo login)
        roleService.invalidateSession(uuid);

        // 3. Salva o tempo online
        Long loginTime = loginTimestamps.remove(uuid);
        if (loginTime != null) {
            long sessionDuration = System.currentTimeMillis() - loginTime;
            if (sessionDuration > 0) {
                try {
                    this.statisticsService.addOnlineTime(uuid, sessionDuration);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao salvar tempo online no quit para " + uuid, e);
                }
            }
        }

        // 4. Limpezas adicionais
        this.preferencesService.removeCachedLanguage(uuid);
        this.roleService.clearSentWarnings(uuid);
    }
}