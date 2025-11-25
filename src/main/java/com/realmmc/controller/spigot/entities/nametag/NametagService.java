package com.realmmc.controller.spigot.entities.nametag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
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

public class NametagService implements Listener, RedisMessageListener {

    private final Logger logger;
    private final RoleService roleService;
    private final ProfileService profileService;
    private final MiniMessage miniMessage;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public NametagService() {
        this.logger = Main.getInstance().getLogger();
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.PROFILES_SYNC.getName().equals(channel) && !RedisChannel.COSMETICS_SYNC.getName().equals(channel)) return;

        try {
            JsonNode node = mapper.readTree(message);
            String uuidStr = node.path("uuid").asText(null);

            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> updateTag(player));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[NametagService] Erro ao processar update Redis", e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            updateTag(event.getPlayer());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                    sendTeamPacket(online, event.getPlayer());
                    sendTeamPacket(event.getPlayer(), online);
                }
            }
        }, 5L);
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
        String teamName = getUniqueTeamName(role, player.getUniqueId());
        playerTeams.put(player.getUniqueId(), teamName);

        String medalId = profile.getEquippedMedal();
        String medalPrefix = "";
        String medalSuffix = "";

        if (medalId != null && !medalId.equalsIgnoreCase("none")) {
            Optional<Medal> medalOpt = Medal.fromId(medalId);
            if (medalOpt.isPresent()) {
                medalPrefix = medalOpt.get().getPrefix();
                medalSuffix = medalOpt.get().getSuffix();
            }
        }

        String rolePrefix = role.getPrefix() != null ? role.getPrefix() : "";
        String roleSuffix = role.getSuffix() != null ? role.getSuffix() : "";
        String colorStr = role.getColor() != null ? role.getColor() : "<gray>";

        String fullPrefixStr = medalPrefix + rolePrefix;
        String fullSuffixStr = roleSuffix + medalSuffix;

        Component tabName = miniMessage.deserialize(fullPrefixStr + colorStr + player.getName() + "<reset>" + fullSuffixStr);
        player.playerListName(tabName);

        Component prefixComponent = miniMessage.deserialize(fullPrefixStr);
        Component suffixComponent = miniMessage.deserialize(fullSuffixStr);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendTeamPacket(viewer, player, teamName, prefixComponent, suffixComponent, role.getColor());
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
                String medalSuffix = "";
                if (medalId != null && !medalId.equalsIgnoreCase("none")) {
                    Optional<Medal> medalOpt = Medal.fromId(medalId);
                    if (medalOpt.isPresent()) {
                        medalPrefix = medalOpt.get().getPrefix();
                        medalSuffix = medalOpt.get().getSuffix();
                    }
                }

                String rolePrefix = role.getPrefix() != null ? role.getPrefix() : "";
                String roleSuffix = role.getSuffix() != null ? role.getSuffix() : "";

                String fullPrefixStr = medalPrefix + rolePrefix;
                String fullSuffixStr = roleSuffix + medalSuffix;

                Component prefixComponent = miniMessage.deserialize(fullPrefixStr);
                Component suffixComponent = miniMessage.deserialize(fullSuffixStr);

                sendTeamPacket(viewer, target, teamName, prefixComponent, suffixComponent, role.getColor());
            });
        });
    }

    private void sendTeamPacket(Player viewer, Player target, String teamName, Component prefix, Component suffix, String colorCode) {
        NamedTextColor teamColor = getNamedTextColor(colorCode);

        WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                prefix,
                suffix,
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                teamColor,
                WrapperPlayServerTeams.OptionData.NONE
        );

        try {
            WrapperPlayServerTeams createPacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.CREATE,
                    Optional.of(info),
                    Collections.singletonList(target.getName())
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, createPacket);
        } catch (Exception ignored) {}

        try {
            WrapperPlayServerTeams updatePacket = new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.UPDATE,
                    Optional.of(info),
                    Collections.emptyList()
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, updatePacket);
        } catch (Exception ignored) {}
    }

    private String getUniqueTeamName(Role role, UUID uuid) {
        int priority = 9999 - role.getWeight();
        return String.format("%04d_%s", priority, uuid.toString().substring(0, 8));
    }

    private NamedTextColor getNamedTextColor(String miniMessageColor) {
        if (miniMessageColor == null || miniMessageColor.isEmpty()) return NamedTextColor.WHITE;
        String cleanColor = miniMessageColor.replace("<", "").replace(">", "");
        NamedTextColor byName = NamedTextColor.NAMES.value(cleanColor.toLowerCase());
        if (byName != null) return byName;

        if (cleanColor.startsWith("#") && cleanColor.length() == 7) {
            try {
                int hex = Integer.parseInt(cleanColor.substring(1), 16);
                return NamedTextColor.nearestTo(TextColor.color(hex));
            } catch (NumberFormatException ignored) {}
        }
        return NamedTextColor.WHITE;
    }
}