package com.realmmc.controller.spigot.entities.cosmetics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.spigot.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MedalService implements Listener, RedisMessageListener {

    private final Logger logger;
    private final ProfileService profileService;
    private final Map<UUID, TextDisplay> activeMedals = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final ServerType currentServerType;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String MEDAL_TAG = "controller_cosmetic_medal";

    public MedalService() {
        this.logger = Main.getInstance().getLogger();
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);

        String typeStr = System.getProperty("map.type");
        if (typeStr != null) {
            try {
                this.currentServerType = ServerType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Tipo de servidor inválido definido em -Dmap.type: " + typeStr);
                throw e;
            }
        } else {
            this.currentServerType = ServerType.PERSISTENT;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    TextDisplay intendedMedal = activeMedals.get(p.getUniqueId());

                    for (Entity passenger : p.getPassengers()) {
                        if (passenger.getScoreboardTags().contains(MEDAL_TAG)) {
                            if (intendedMedal == null || !passenger.getUniqueId().equals(intendedMedal.getUniqueId())) {
                                passenger.remove();
                            }
                        }
                    }

                    if (intendedMedal != null) {
                        if (!intendedMedal.isValid()) {
                            activeMedals.remove(p.getUniqueId());
                            updateMedal(p);
                            continue;
                        }

                        if (!p.getPassengers().contains(intendedMedal)) {
                            p.addPassenger(intendedMedal);
                        }
                    }
                }
            }
        }.runTaskTimer(Main.getInstance(), 10L, 20L);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel)) return;

        try {
            JsonNode node = mapper.readTree(message);
            String uuidStr = node.path("uuid").asText(null);

            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> updateMedal(player));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[MedalService] Erro ao processar update Redis", e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> updateMedal(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeMedal(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> updateMedal(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> updateMedal(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = activeMedals.get(player.getUniqueId());

        if (display != null && display.isValid()) {
            if (event.isSneaking()) {
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setTextOpacity((byte) 100);
            } else {
                display.setDefaultBackground(true);
                display.setTextOpacity((byte) -1);
            }
        }
    }

    public void updateMedal(Player player) {
        if (!player.isOnline()) return;

        removeMedal(player);

        profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
            String medalId = profile.getEquippedMedal();
            Optional<Medal> medalOpt = Medal.fromId(medalId);

            if (medalOpt.isEmpty() || medalOpt.get() == Medal.NONE) {
                return;
            }

            Medal medal = medalOpt.get();

            if (!medal.getAllowedTypes().isEmpty() && !medal.getAllowedTypes().contains(currentServerType)) {
                return;
            }

            Bukkit.getScheduler().runTask(Main.getInstance(), () -> renderMedal(player, medal));
        });
    }

    private void renderMedal(Player player, Medal medal) {
        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class);

        display.text(mm.deserialize(medal.getDisplayName()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.addScoreboardTag(MEDAL_TAG);

        if (player.isSneaking()) {
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setTextOpacity((byte) 100);
        } else {
            display.setDefaultBackground(true);
            display.setTextOpacity((byte) -1);
        }

        Transformation trans = display.getTransformation();
        trans.getScale().set(1.0f);
        trans.getTranslation().set(0.0f, 0.65f, 0.0f);

        display.setTransformation(trans);
        display.setPersistent(false);

        player.addPassenger(display);

        activeMedals.put(player.getUniqueId(), display);
        logger.info("[MedalService] Medalha " + medal.getId() + " renderizada para " + player.getName());
    }

    public void removeMedal(Player player) {
        TextDisplay display = activeMedals.remove(player.getUniqueId());
        if (display != null) {
            if (player.isValid()) {
                player.removePassenger(display);
            }
            display.remove();
        }

        for (Entity passenger : player.getPassengers()) {
            if (passenger.getScoreboardTags().contains(MEDAL_TAG)) {
                passenger.remove();
            }
        }
    }

    public void removeAll() {
        activeMedals.values().forEach(Entity::remove);
        activeMedals.clear();
        logger.info("[MedalService] Todas as medalhas foram removidas.");
    }
}