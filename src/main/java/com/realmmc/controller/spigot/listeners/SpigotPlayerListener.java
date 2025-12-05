package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.RoleKickHandler;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        if (sessionTrackerServiceOpt.isEmpty()) {
            logger.warning("SessionTrackerService não encontrado! Rastreamento de sessão não funcionará.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();

        loginTimestamps.put(uuid, System.currentTimeMillis());

        TaskScheduler.runAsync(() -> {
            try {
                preferencesService.loadAndCachePreferences(uuid);

                Optional<Profile> profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isPresent()) {
                    Profile profile = profileOpt.get();
                    String ip = null;
                    try { ip = event.getAddress().getHostAddress(); } catch (Exception e) { }

                    boolean needsSave = false;
                    if (ip != null && !ip.isEmpty() && !ip.equals(profile.getLastIp())) {
                        profile.setLastIp(ip);
                        if (!profile.getIpHistory().contains(ip)) {
                            profile.getIpHistory().add(ip);
                        }
                        needsSave = true;
                    }
                    if (System.currentTimeMillis() - profile.getLastLogin() > 1000) {
                        profile.setLastLogin(System.currentTimeMillis());
                        needsSave = true;
                    }

                    if (needsSave) {
                        profileService.save(profile);
                    }
                } else {
                    logger.warning("[SpigotPlayerListener] Perfil não encontrado no DB para " + uuid + ". O Velocity (Proxy) deveria tê-lo criado. O jogador pode ter problemas de permissão.");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro CRÍTICO ao tentar atualizar lastLogin/IP para " + playerName + " (" + uuid + ")", e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinFinalize(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        this.roleService.clearSentWarnings(uuid);
        this.roleService.checkAndSendLoginExpirationWarning(player);
        this.preferencesService.checkAndSendStaffChatWarning(player, uuid);

        sessionTrackerServiceOpt.ifPresent(service -> {
            try {
                int currentPing = player.getPing();
                service.setSessionField(uuid, "ping", String.valueOf(currentPing));
                service.setSessionState(uuid, AuthenticationGuard.STATE_ONLINE);
            } catch (Exception e) {
                logger.log(Level.FINEST, "Não foi possível atualizar ping inicial para " + player.getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        RoleKickHandler.cancelKick(uuid);
        roleService.invalidateSession(uuid);

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

        // CORREÇÃO: Atualização atômica/parcial para evitar sobrescrever a senha
        TaskScheduler.runAsync(() -> {
            profileService.updateLastLogout(uuid);
        });

        this.preferencesService.removeCachedPreferences(uuid);
        this.roleService.clearSentWarnings(uuid);
    }
}