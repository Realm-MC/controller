package com.palacesky.controller.spigot.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.role.PlayerRole;
import com.palacesky.controller.shared.role.Role;
import com.palacesky.controller.shared.role.RoleType;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import com.palacesky.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

@Listeners
public class RoleListener implements Listener, RedisMessageListener {

    private final RoleService roleService;
    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isLoginServer;

    public RoleListener() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.isLoginServer = checkIsLoginServer();

        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.ROLE_NOTIFICATION, this));
    }

    private boolean checkIsLoginServer() {
        String mapType = System.getProperty("MAP_TYPE");
        if (mapType != null && mapType.equalsIgnoreCase("login")) return true;

        String serverId = System.getProperty("controller.serverId");
        if (serverId != null && serverId.toLowerCase().startsWith("login")) return true;

        return Bukkit.getServer().getName().toLowerCase().startsWith("login");
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ROLE_NOTIFICATION.getName().equals(channel)) return;

        if (isLoginServer) return;

        try {
            JsonNode node = mapper.readTree(message);
            UUID uuid = UUID.fromString(node.get("uuid").asText());
            String roleName = node.get("role").asText();

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && AuthenticationGuard.isAuthenticated(uuid)) {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> notifyPlayer(player, roleName));
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Erro ao processar notificação de role", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isLoginServer) return;

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            roleService.checkExpiration(uuid);

            if (!AuthenticationGuard.isAuthenticated(uuid)) {
                return;
            }

            profileService.getByUuid(uuid).ifPresent(profile -> {
                if (profile.getRoles() != null) {
                    for (PlayerRole pr : profile.getRoles()) {
                        if (pr.isPendingNotification() && !pr.hasExpired()) {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> notifyPlayer(player, pr.getRoleName()));
                        }
                    }
                }
            });
        });
    }

    private void notifyPlayer(Player player, String roleName) {
        if (isLoginServer || !AuthenticationGuard.isAuthenticated(player.getUniqueId())) {
            return;
        }

        Optional<Role> roleOpt = roleService.getRole(roleName);
        if (roleOpt.isEmpty()) return;
        Role role = roleOpt.get();

        MessageKey titleKey;
        MessageKey subKey;
        String sound;

        if (role.getType() == RoleType.VIP) {
            titleKey = MessageKey.ROLE_ACTIVATED_VIP_TITLE;
            subKey = MessageKey.ROLE_ACTIVATED_VIP_SUBTITLE;
            sound = SoundKeys.SUCCESS;
        } else if (role.getType() == RoleType.STAFF) {
            titleKey = MessageKey.ROLE_ACTIVATED_STAFF_TITLE;
            subKey = MessageKey.ROLE_ACTIVATED_STAFF_SUBTITLE;
            sound = SoundKeys.NOTIFICATION;
        } else {
            titleKey = MessageKey.ROLE_ACTIVATED_DEFAULT_TITLE;
            subKey = MessageKey.ROLE_ACTIVATED_DEFAULT_SUBTITLE;
            sound = SoundKeys.CLICK;
        }

        Messages.sendTitle(player,
                Message.of(titleKey),
                Message.of(subKey).with("group", role.getDisplayName()));

        Messages.send(player, Message.of(MessageKey.ROLE_ACTIVATED_OFFLINE_MESSAGE).with("group", role.getDisplayName()));

        soundPlayer.ifPresent(sp -> sp.playSound(player, sound));

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            roleService.markNotificationAsRead(player.getUniqueId(), roleName);
        });
    }
}