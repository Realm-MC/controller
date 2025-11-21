package com.realmmc.controller.spigot.entities.nametag;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.spigot.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NametagService implements Listener {

    private final Logger logger;
    private final RoleService roleService;
    private final ProfileService profileService;
    private final MiniMessage miniMessage;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();

    public NametagService() {
        this.logger = Main.getInstance().getLogger();
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateTag(event.getPlayer());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                sendTeamPacket(event.getPlayer(), online);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerTeams.remove(event.getPlayer().getUniqueId());
    }

    public void updateTag(Player player) {
        if (player == null || !player.isOnline()) return;

        roleService.loadPlayerDataAsync(player.getUniqueId()).thenAccept(session -> {
            if (session == null) return;

            profileService.getByUuid(player.getUniqueId()).ifPresent(profile -> {
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    applyTag(player, session, profile);
                });
            });
        });
    }

    private void applyTag(Player player, PlayerSessionData session, Profile profile) {
        Role role = session.getPrimaryRole();
        String teamName = getTeamName(role);
        String medalId = profile.getEquippedMedal();

        playerTeams.put(player.getUniqueId(), teamName);

        String medalPrefix = "";
        if (medalId != null && !medalId.equalsIgnoreCase("none")) {
            Optional<Medal> medalOpt = Medal.fromId(medalId);
            if (medalOpt.isPresent()) {
                medalPrefix = medalOpt.get().getPrefix();
            }
        }

        String rolePrefix = role.getPrefix() != null ? role.getPrefix() : "";
        String colorStr = role.getColor() != null ? role.getColor() : "<gray>";

        String fullPrefixStr = medalPrefix + rolePrefix;

        Component prefixComponent = miniMessage.deserialize(fullPrefixStr);
        Component displayName = miniMessage.deserialize(fullPrefixStr + colorStr + player.getName());

        player.playerListName(displayName);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendTeamPacket(viewer, player, teamName, prefixComponent, role.getColor());
        }
    }

    private void sendTeamPacket(Player viewer, Player target) {
        String teamName = playerTeams.get(target.getUniqueId());
        if (teamName == null) return;

        roleService.getSessionDataFromCache(target.getUniqueId()).ifPresent(session -> {
            profileService.getByUuid(target.getUniqueId()).ifPresent(profile -> {
                Role role = session.getPrimaryRole();
                String medalId = profile.getEquippedMedal();

                String medalPrefix = "";
                if (medalId != null && !medalId.equalsIgnoreCase("none")) {
                    Optional<Medal> medalOpt = Medal.fromId(medalId);
                    if (medalOpt.isPresent()) {
                        medalPrefix = medalOpt.get().getPrefix();
                    }
                }

                String rolePrefix = role.getPrefix() != null ? role.getPrefix() : "";
                String fullPrefixStr = medalPrefix + rolePrefix;

                Component prefixComponent = miniMessage.deserialize(fullPrefixStr);
                sendTeamPacket(viewer, target, teamName, prefixComponent, role.getColor());
            });
        });
    }

    private void sendTeamPacket(Player viewer, Player target, String teamName, Component prefix, String colorCode) {
        NamedTextColor teamColor = getNamedTextColor(colorCode);

        WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                prefix,
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                teamColor,
                WrapperPlayServerTeams.OptionData.NONE
        );

        WrapperPlayServerTeams createPacket = new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                Optional.of(info),
                Collections.singletonList(target.getName())
        );

        WrapperPlayServerTeams addPacket = new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                Optional.empty(),
                Collections.singletonList(target.getName())
        );

        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, createPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, addPacket);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send nametag packet to " + viewer.getName(), e);
        }
    }

    private String getTeamName(Role role) {
        int priority = 9999 - role.getWeight();
        return String.format("%04d_%s", priority, role.getName());
    }

    private NamedTextColor getNamedTextColor(String miniMessageColor) {
        if (miniMessageColor == null || miniMessageColor.isEmpty()) {
            return NamedTextColor.WHITE;
        }

        String cleanColor = miniMessageColor.replace("<", "").replace(">", "");

        NamedTextColor byName = NamedTextColor.NAMES.value(cleanColor.toLowerCase());
        if (byName != null) {
            return byName;
        }

        if (cleanColor.startsWith("#") && cleanColor.length() == 7) {
            try {
                int hex = Integer.parseInt(cleanColor.substring(1), 16);
                return NamedTextColor.nearestTo(TextColor.color(hex));
            } catch (NumberFormatException ignored) {
            }
        }

        return NamedTextColor.WHITE;
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTag(p);
        }
    }
}