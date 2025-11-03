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
import com.realmmc.controller.shared.utils.TaskScheduler;
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

        // Remoção da deteção de API (Geyser/ViaVersion) pois não são mais usadas aqui
        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("SessionTrackerService não encontrado! Rastreamento de sessão não funcionará.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            sessionTrackerServiceOpt.ifPresent(service -> service.endSession(event.getPlayer().getUniqueId(), event.getPlayer().getName()));
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // Define o estado como CONECTANDO (o SessionService definirá como ONLINE após carregar)
        sessionTrackerServiceOpt.ifPresent(service ->
                service.setSessionState(uuid, AuthenticationGuard.STATE_CONNECTING)
        );

        // Armazena o tempo de login para estatísticas
        loginTimestamps.put(uuid, System.currentTimeMillis());

        // Apenas atualiza o lastIp e lastLogin no perfil existente (NÃO CRIA)
        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isPresent()) {
                    Profile profile = profileOpt.get();
                    String ip = null;
                    try { ip = event.getAddress().getHostAddress(); } catch (Exception e) { /* ignora */ }

                    boolean needsSave = false;
                    if (ip != null && !ip.isEmpty() && !ip.equals(profile.getLastIp())) {
                        profile.setLastIp(ip);
                        if (!profile.getIpHistory().contains(ip)) {
                            profile.getIpHistory().add(ip);
                        }
                        needsSave = true;
                    }
                    if (System.currentTimeMillis() - profile.getLastLogin() > 1000) { // Evita saves desnecessários
                        profile.setLastLogin(System.currentTimeMillis());
                        needsSave = true;
                    }

                    if (needsSave) {
                        profileService.save(profile);
                    }
                } else {
                    // Este é o log de aviso que você viu.
                    logger.warning("[SpigotPlayerListener] Perfil não encontrado no DB para " + uuid + ". O Velocity (Proxy) deveria tê-lo criado. O jogador pode ter problemas de permissão.");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro CRÍTICO ao tentar atualizar lastLogin/IP para " + playerName + " (" + uuid + ")", e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinFinalize(PlayerJoinEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        this.roleService.clearSentWarnings(uuid);

        // Atualiza o ping no SessionTracker (agora que o jogador está totalmente no servidor)
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

        // Limpa a sessão no Redis
        sessionTrackerServiceOpt.ifPresent(service -> service.endSession(uuid, username));

        // Invalida o cache de sessão local do RoleService
        roleService.invalidateSession(uuid);

        // Salva o tempo online
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

        // Limpezas adicionais
        this.preferencesService.removeCachedLanguage(uuid);
        this.roleService.clearSentWarnings(uuid);
    }
}