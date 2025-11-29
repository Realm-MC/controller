package com.realmmc.controller.spigot.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.spigot.Main;
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
public class CashListener implements Listener, RedisMessageListener {

    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayer;
    private final ObjectMapper mapper = new ObjectMapper();

    public CashListener() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class);

        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.CASH_NOTIFICATION, this));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
                int pending = profile.getPendingCash();
                if (pending > 0) {
                    notifyAndReset(player, pending);
                }
            });
        });
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.CASH_NOTIFICATION.getName().equals(channel)) return;

        try {
            JsonNode node = mapper.readTree(message);
            UUID uuid = UUID.fromString(node.get("uuid").asText());
            int amount = node.get("amount").asInt();

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                notifyAndReset(player, amount);
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Erro ao processar notificação de cash", e);
        }
    }

    private void notifyAndReset(Player player, int amount) {
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Messages.send(player, MessageKey.CASH_INFO_PENDING_TITLE);
            Messages.send(player, Message.of(MessageKey.CASH_INFO_PENDING_MESSAGE).with("amount", amount));
            soundPlayer.ifPresent(sp -> sp.playSound(player, SoundKeys.SUCCESS));
        });

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            profileService.resetPendingCash(player.getUniqueId());
        });
    }
}