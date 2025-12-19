package com.palacesky.controller.spigot.cash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.palacesky.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotCashCache implements Listener, RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(SpigotCashCache.class.getName());
    private final Map<UUID, Integer> cashCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayer;
    private final boolean isLoginServer;

    public SpigotCashCache() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.isLoginServer = checkIsLoginServer();

        TaskScheduler.runAsyncTimer(this::checkPendingCashForOnlinePlayers, 5, 5, TimeUnit.SECONDS);
    }

    private boolean checkIsLoginServer() {
        String mapType = System.getProperty("MAP_TYPE");
        if (mapType != null && mapType.equalsIgnoreCase("login")) return true;

        String serverId = System.getProperty("controller.serverId");
        if (serverId != null && serverId.toLowerCase().startsWith("login")) return true;

        return Bukkit.getServer().getName().toLowerCase().startsWith("login");
    }

    public int getCachedCash(UUID uuid) {
        return cashCache.getOrDefault(uuid, 0);
    }

    public void updateCache(UUID uuid, int cash) {
        cashCache.put(uuid, cash);
    }

    public void removeCache(UUID uuid) {
        cashCache.remove(uuid);
    }

    private void checkPendingCashForOnlinePlayers() {
        if (isLoginServer) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!AuthenticationGuard.isAuthenticated(player.getUniqueId())) continue;

            try {
                profileService.getByUuid(player.getUniqueId()).ifPresent(p -> {
                    cashCache.put(player.getUniqueId(), p.getCash());

                    if (p.getPendingCash() > 0) {
                        notifyAndReset(player, p.getPendingCash());
                    }
                });
            } catch (Exception e) {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isPresent()) {
                    Profile p = profileOpt.get();
                    cashCache.put(uuid, p.getCash());

                    if (p.getPendingCash() > 0) {
                        notifyAndReset(player, p.getPendingCash());
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[SpigotCashCache] Erro ao carregar cash inicial para " + player.getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeCache(event.getPlayer().getUniqueId());
    }

    @Override
    public void onMessage(String channel, String message) {
        boolean isProfileSync = RedisChannel.PROFILES_SYNC.getName().equals(channel);
        boolean isCashNotify = RedisChannel.CASH_NOTIFICATION.getName().equals(channel);

        if (!isProfileSync && !isCashNotify) return;

        try {
            JsonNode node = mapper.readTree(message);
            String uuidStr = node.path("uuid").asText(null);
            if (uuidStr == null) return;
            UUID uuid = UUID.fromString(uuidStr);

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            if (isCashNotify) {
                int amountAdded = node.path("amount").asInt(0);
                TaskScheduler.runAsync(() -> {
                    profileService.getByUuid(uuid).ifPresent(p -> {
                        cashCache.put(uuid, p.getCash());
                        notifyAndReset(player, amountAdded);
                    });
                });

            } else if (isProfileSync) {
                String action = node.path("action").asText("");
                if ("upsert".equals(action)) {
                    JsonNode cashNode = node.path("cash");
                    if (cashNode.isInt()) {
                        cashCache.put(uuid, cashNode.asInt());
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao processar atualização de cash no SpigotCashCache", e);
        }
    }

    private void notifyAndReset(Player player, int amount) {
        if (isLoginServer) {
            return;
        }

        if (!AuthenticationGuard.isAuthenticated(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (!player.isOnline()) return;

            if (isLoginServer || !AuthenticationGuard.isAuthenticated(player.getUniqueId())) return;

            Messages.sendTitle(player, MessageKey.CASH_INFO_PENDING_TITLE, MessageKey.CASH_INFO_PENDING_MESSAGE);
            Messages.send(player, Message.of(MessageKey.CASH_INFO_PENDING_MESSAGE).with("amount", amount));
            soundPlayer.ifPresent(sp -> sp.playSound(player, SoundKeys.SUCCESS));
        });

        TaskScheduler.runAsync(() -> {
            if (!player.isOnline()) return;
            profileService.resetPendingCash(player.getUniqueId());
        });
    }
}