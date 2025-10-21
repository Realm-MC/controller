package com.realmmc.controller.modules.permission;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.shared.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class RoleExpirationModule extends AbstractCoreModule {

    private BukkitTask expirationTask;
    private ProfileService profileService;
    private Optional<SoundPlayer> soundPlayerOpt;

    private static final long CHECK_INTERVAL_TICKS = 20L * 60;
    private static final long SOON_CHECK_INTERVAL_TICKS = 20L * 60 * 60;
    private static final long EXPIRING_SOON_THRESHOLD = TimeUnit.DAYS.toMillis(1);
    private final Map<UUID, Set<Integer>> notifiedSoon = new ConcurrentHashMap<>();

    public RoleExpirationModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "RoleExpiration";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Verifica e notifica sobre cargos temporários expirados ou expirando em breve.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Permission", "Profile", "Sound"};
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Iniciando tarefas de verificação de expiração de cargos...");
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado para RoleExpirationModule!"));
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        if (soundPlayerOpt.isEmpty()) {
            logger.warning("SoundPlayer não encontrado! Notificações sonoras de expiração estarão desativadas.");
        }
        startExpirationTasks();
    }

    @Override
    protected void onDisable() {
        if (expirationTask != null) {
            expirationTask.cancel();
            logger.info("Tarefas de verificação de expiração de cargos paradas.");
        }
        notifiedSoon.clear();
        PlayerQuitEvent.getHandlerList().unregister(Main.getInstance());
    }

    private void startExpirationTasks() {
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), this::checkExpiredRoles, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);

        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), this::checkSoonExpiringRoles, SOON_CHECK_INTERVAL_TICKS, SOON_CHECK_INTERVAL_TICKS);

        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                notifiedSoon.remove(e.getPlayer().getUniqueId());
            }
        }, Main.getInstance());
    }

    private void checkExpiredRoles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
                List<Role> expiredRoles = profileService.getAndRemoveExpiredRoles(profile);
                if (!expiredRoles.isEmpty()) {
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        for (Role expiredRole : expiredRoles) {
                            String groupDisplayName = expiredRole.getDisplayName() != null ? expiredRole.getDisplayName() : expiredRole.getName();
                            Messages.send(player, Message.of(MessageKey.PROFILE_ROLE_JUST_EXPIRED)
                                    .with("group_displayname", groupDisplayName));
                        }
                        playSound(player, SoundKeys.NOTIFICATION);
                    });
                }
            });
        }
    }

    private void checkSoonExpiringRoles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
                List<Map.Entry<Role, Long>> soonExpiring = profileService.getRolesExpiringSoon(profile, EXPIRING_SOON_THRESHOLD);

                if (!soonExpiring.isEmpty()) {
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        Set<Integer> alreadyNotified = notifiedSoon.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
                        boolean playSound = false;

                        for (Map.Entry<Role, Long> entry : soonExpiring) {
                            Role role = entry.getKey();
                            if (alreadyNotified.add(role.getId())) {
                                long timeLeftMillis = entry.getValue();
                                String timeLeftFormatted = TimeUtils.formatDuration(timeLeftMillis);
                                String groupDisplayName = role.getDisplayName() != null ? role.getDisplayName() : role.getName();

                                Messages.send(player, Message.of(MessageKey.PROFILE_ROLE_EXPIRING_SOON)
                                        .with("group_displayname", groupDisplayName)
                                        .with("time_left", timeLeftFormatted));
                                playSound = true;
                            }
                        }
                        if(playSound) {
                            playSound(player, SoundKeys.NOTIFICATION);
                        }
                    });
                }
            });
        }
    }

    private void playSound(Player player, String key) {
        soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
    }
}