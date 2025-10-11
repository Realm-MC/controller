package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.utils.TimeUtils;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Listeners
public class RoleNotifierListener implements Listener {

    private final String storeUrl = "https://loja.realmmc.com.br";
    private final MiniMessage mm = MiniMessage.miniMessage();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            ProfileService profileService = ServiceRegistry.getInstance().getService(ProfileService.class).orElse(null);
            RoleService roleService = ServiceRegistry.getInstance().getService(RoleService.class).orElse(null);
            SoundService soundService = ServiceRegistry.getInstance().getService(SoundService.class).orElse(null);

            if (profileService == null || roleService == null || soundService == null) return;

            profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
                List<Role> expiredVips = profileService.getAndRemoveExpiredVipRoles(profile);
                if (!expiredVips.isEmpty()) {
                    Role highestExpiredVip = expiredVips.stream().max(Comparator.comparingInt(Role::getWeight)).get();
                    sendExpiredMessage(player, highestExpiredVip);
                    soundService.playSound(player, SoundKeys.USAGE_ERROR);
                    return;
                }

                long twentyFourHoursInMillis = TimeUnit.HOURS.toMillis(24);
                long now = System.currentTimeMillis();

                profile.getRoleIds().stream()
                        .map(roleId -> roleService.getById(roleId).orElse(null))
                        .filter(role -> role != null && role.getType() == RoleType.VIP)
                        .map(role -> {
                            Long expirationTime = profile.getRoleExpirations().get(String.valueOf(role.getId()));
                            return expirationTime != null ? Map.entry(role, expirationTime) : null;
                        })
                        .filter(Objects::nonNull)
                        .min(Comparator.comparingLong(Map.Entry::getValue))
                        .ifPresent(entry -> {
                            long remaining = entry.getValue() - now;
                            if (remaining > 0 && remaining <= twentyFourHoursInMillis) {
                                sendWarningMessage(player, entry.getKey(), remaining);
                                soundService.playSound(player, SoundKeys.USAGE_ERROR);
                            }
                        });
            });
        }, 20L * 60);
    }

    private void sendWarningMessage(Player player, Role role, long remainingMillis) {
        String timeExpired = TimeUtils.formatDuration(remainingMillis);
        String messageStr = """
                <newline>
                <red>O seu VIP <white><group_displayname></white> <red>vai expirar dentro de <time_expired><red>!
                <gray>Você pode renovar ou adquirir um novo VIP em <underlined>loja.realmmc.com.br</underlined>.</gray>
                <newline>
                """;

        Component message = mm.deserialize(messageStr,
                Placeholder.unparsed("group_displayname", role.getDisplayName()),
                Placeholder.unparsed("time_expired", timeExpired)
        ).clickEvent(ClickEvent.openUrl(storeUrl));

        player.sendMessage(message);
    }

    private void sendExpiredMessage(Player player, Role role) {
        String messageStr = """
                <newline>
                <red>Parece que o seu VIP <white><group_displayname></white> <red>acabou de expirar!
                <gray>Você pode renovar ou adquirir um novo VIP em <underlined>loja.realmmc.com.br</underlined>.</gray>
                <newline>
                """;

        Component message = mm.deserialize(messageStr,
                Placeholder.unparsed("group_displayname", role.getDisplayName())
        ).clickEvent(ClickEvent.openUrl(storeUrl));

        player.sendMessage(message);
    }
}