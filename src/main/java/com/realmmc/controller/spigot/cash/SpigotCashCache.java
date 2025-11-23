package com.realmmc.controller.spigot.cash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.utils.TaskScheduler;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotCashCache implements Listener, RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(SpigotCashCache.class.getName());
    private final Map<UUID, Integer> cashCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProfileService profileService;

    public SpigotCashCache() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isPresent()) {
                    int cash = profileOpt.get().getCash();
                    cashCache.put(uuid, cash);
                    LOGGER.finer("[SpigotCashCache] Cache de cash (valor: " + cash + ") populado para " + player.getName());
                } else {
                    LOGGER.warning("[SpigotCashCache] Perfil não encontrado no MongoDB para " + player.getName() + " no evento de join.");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[SpigotCashCache] Falha ao carregar cash no cache durante o join de " + player.getName(), e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeCache(event.getPlayer().getUniqueId());
        LOGGER.finer("[SpigotCashCache] Cache de cash limpo para " + event.getPlayer().getName());
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel)) {
            return;
        }

        try {
            JsonNode node = mapper.readTree(message);
            String action = node.path("action").asText("");

            if (!"upsert".equals(action)) {
                return;
            }

            String uuidStr = node.path("uuid").asText(null);
            if (uuidStr == null) {
                return;
            }

            UUID uuid = UUID.fromString(uuidStr);

            if (cashCache.containsKey(uuid)) {
                JsonNode cashNode = node.path("cash");
                if (cashNode.isInt()) {
                    int newCash = cashNode.asInt();
                    cashCache.put(uuid, newCash);
                    LOGGER.finer("[SpigotCashCache] Cache de cash atualizado via Redis para " + uuid + " (Novo Saldo: " + newCash + ")");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao processar mensagem PROFILES_SYNC no SpigotCashCache", e);
        }
    }
}