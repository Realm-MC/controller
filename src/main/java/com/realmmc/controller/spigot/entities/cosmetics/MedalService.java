package com.realmmc.controller.spigot.entities.cosmetics;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.ServerType;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
import com.realmmc.controller.shared.preferences.MedalVisibility;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.spigot.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MedalService implements Listener {

    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final Map<UUID, TextDisplay> activeMedals = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final ServerType currentServerType;

    public MedalService() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);

        String typeStr = System.getProperty("map.type");
        if (typeStr != null) {
            try {
                this.currentServerType = ServerType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                Main.getInstance().getLogger().warning("Tipo de servidor inválido: " + typeStr);
                throw e;
            }
        } else {
            this.currentServerType = ServerType.PERSISTENT;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (activeMedals.containsKey(p.getUniqueId())) {
                        TextDisplay display = activeMedals.get(p.getUniqueId());
                        if (display == null || !display.isValid()) {
                            removeMedal(p);
                            updateMedal(p);
                            continue;
                        }
                        if (p.isDead() || !p.isValid()) {
                            display.setVisibleByDefault(false);
                        } else {
                            display.setVisibleByDefault(true);
                            display.teleport(p.getLocation().add(0, 2.35, 0));
                        }
                    }
                }
            }
        }.runTaskTimer(Main.getInstance(), 20L, 20L);
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

    public void updateMedal(Player player) {
        if (!player.isOnline()) return;

        MedalVisibility vis = preferencesService.getCachedMedalVisibility(player.getUniqueId()).orElse(MedalVisibility.ALL);
        if (vis == MedalVisibility.PREFIX_ONLY || vis == MedalVisibility.NONE) {
            removeMedal(player);
            return;
        }

        profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
            String medalId = profile.getEquippedMedal();
            Optional<Medal> medalOpt = Medal.fromId(medalId);

            if (medalOpt.isEmpty() || medalOpt.get() == Medal.NONE) {
                removeMedal(player);
                return;
            }

            Medal medal = medalOpt.get();

            if (!medal.isVisibleOn(currentServerType)) {
                removeMedal(player);
                return;
            }

            renderMedal(player, medal);
        });
    }

    private void renderMedal(Player player, Medal medal) {
        removeMedal(player);

        TextDisplay display = player.getWorld().spawn(player.getLocation().add(0, 2.35, 0), TextDisplay.class);
        display.text(mm.deserialize(medal.getDisplayName()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setDefaultBackground(false);
        display.setShadowed(true);

        Transformation trans = display.getTransformation();
        trans.getScale().set(1.0f);
        trans.getTranslation().set(0.0f, 0.5f, 0.0f);
        display.setTransformation(trans);

        display.setPersistent(false);

        player.addPassenger(display);

        activeMedals.put(player.getUniqueId(), display);
    }

    public void removeMedal(Player player) {
        TextDisplay display = activeMedals.remove(player.getUniqueId());
        if (display != null) {
            player.removePassenger(display);
            display.remove();
        }
    }

    public void removeAll() {
        activeMedals.values().forEach(Entity::remove);
        activeMedals.clear();
    }
}