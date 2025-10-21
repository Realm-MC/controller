package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.realmmc.controller.shared.utils.TimeUtils;

@Listeners
public class RoleNotifierListener {

    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private static final Logger LOGGER = Logger.getLogger(RoleNotifierListener.class.getName());

    private static final long EXPIRING_SOON_THRESHOLD_LOGIN = TimeUnit.DAYS.toMillis(1);

    public RoleNotifierListener() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService not found for RoleNotifierListener!"));
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        if (soundPlayerOpt.isEmpty()){
            LOGGER.warning("SoundPlayer service not found! Role expiration sounds will be disabled.");
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {

            List<Role> expiredRoles = profileService.getAndRemoveExpiredRoles(profile);

            if (!expiredRoles.isEmpty()) {
                for (Role expiredRole : expiredRoles) {
                    String groupDisplayName = expiredRole.getDisplayName() != null ? expiredRole.getDisplayName() : expiredRole.getName();
                    Messages.send(player, Message.of(MessageKey.PROFILE_ROLE_JUST_EXPIRED)
                            .with("group_displayname", groupDisplayName));
                }
                playSound(player, "NOTIFICATION");
            }

            List<Map.Entry<Role, Long>> soonExpiringRoles = profileService.getRolesExpiringSoon(profile, EXPIRING_SOON_THRESHOLD_LOGIN);

            if (!soonExpiringRoles.isEmpty()) {
                boolean playedSound = false;
                for (Map.Entry<Role, Long> entry : soonExpiringRoles) {
                    Role role = entry.getKey();
                    long timeLeftMillis = entry.getValue();
                    String timeLeftFormatted = TimeUtils.formatDuration(timeLeftMillis);
                    String groupDisplayName = role.getDisplayName() != null ? role.getDisplayName() : role.getName();

                    Messages.send(player, Message.of(MessageKey.PROFILE_ROLE_EXPIRING_SOON)
                            .with("group_displayname", groupDisplayName)
                            .with("time_left", timeLeftFormatted));

                    if (!playedSound) {
                        playSound(player, "NOTIFICATION");
                        playedSound = true;
                    }
                }
            }
        });
    }

    private void playSound(Player player, String key) {
        soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
    }
}