package com.realmmc.controller.spigot.entities.npcs;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NPCService implements Listener {
    private final NPCConfigLoader configLoader;
    private final Map<String, NPCData> globalNPCs = new ConcurrentHashMap<>();
    private final MojangSkinResolver mojangResolver = new MojangSkinResolver();
    private final MineskinResolver mineskinResolver = new MineskinResolver();
    private final Map<UUID, List<UUID>> nameHolograms = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();

    public NPCService() {
        this.configLoader = new NPCConfigLoader();
        this.configLoader.load();
        loadSavedNPCs();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        resendAllTo(event.getPlayer());
    }

    public void resendAllTo(Player player) {
        for (NPCData npc : globalNPCs.values()) {
            try {
                sendNPCPackets(player, npc);
            } catch (Exception ignored) {}
        }
    }

    public void despawnAllFor(Player player) {
        if (globalNPCs.isEmpty()) return;
        int[] ids = globalNPCs.values().stream().mapToInt(NPCData::getEntityId).toArray();
        try {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(ids);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);
        } catch (Exception ignored) {}
    }

    public void despawnAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            despawnAllFor(p);
        }
        try {
            var holo = Main.getInstance().getHologramService();
            for (List<UUID> ids : nameHolograms.values()) {
                holo.removeByUUIDs(ids);
            }
        } catch (Throwable ignored) {}
        nameHolograms.clear();
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            for (NPCData npc : globalNPCs.values()) {
                Team t = sb.getTeam("npc_hide_" + npc.getEntityId());
                if (t != null) t.unregister();
            }
        } catch (Throwable ignored) {}
    }

    public void reloadAll() {
        despawnAll();
        this.configLoader.load();
        this.globalNPCs.clear();
        loadSavedNPCs();
        for (Player p : Bukkit.getOnlinePlayers()) {
            resendAllTo(p);
        }
    }

    public boolean isNameHologram(java.util.UUID hologramUuid) {
        for (java.util.List<java.util.UUID> ids : nameHolograms.values()) {
            if (ids != null && ids.contains(hologramUuid)) return true;
        }
        return false;
    }

    private void loadSavedNPCs() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;
                Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(), entry.getYaw(), entry.getPitch());
                String id = entry.getId();
                String displayName = entry.getMessage();
                String skin = entry.getItem() != null ? entry.getItem() : "default";
                NPCData npcData = createNPC(id, location, displayName, skin, entry.getTexturesValue(), entry.getTexturesSignature());
                if (npcData != null) {
                    globalNPCs.put(id, npcData);
                    spawnNameHologram(npcData);
                }
            } catch (Exception e) {
                System.err.println("Erro ao carregar NPC com ID de config " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private NPCData createNPC(String id, Location location, String displayName, String skinSource, String texturesValue, String texturesSignature) {
        try {
            UUID npcUUID = UUID.randomUUID();
            int entityId = ThreadLocalRandom.current().nextInt(100000, 999999);
            String internalName = (id != null && !id.isEmpty()) ? id : "NPC_" + entityId;
            UserProfile profile = new UserProfile(npcUUID, internalName);

            if (!"player".equalsIgnoreCase(skinSource)) {
                TextureProperty texture = null;
                if (skinSource != null && (skinSource.startsWith("http://") || skinSource.startsWith("https://"))) {
                    texture = mineskinResolver.resolveFromUrl(skinSource);
                } else if (skinSource != null && !"default".equalsIgnoreCase(skinSource)) {
                    texture = mojangResolver.resolveByNameOrUuid(skinSource);
                }

                if (texture != null) {
                    profile.getTextureProperties().add(texture);
                }
            }

            return new NPCData(npcUUID, entityId, profile, location, displayName, skinSource);
        } catch (Exception e) {
            System.err.println("Erro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void spawnNameHologram(NPCData npc) {
        try {
            List<UUID> old = nameHolograms.remove(npc.getUuid());
            if (old != null) {
                Main.getInstance().getHologramService().removeByUUIDs(old);
            }
            Location base = npc.getLocation().clone().add(0, 2.0, 0);

            String displayName = (npc.getName() != null ? npc.getName() : "NPC");
            Component formattedComponent = legacySerializer.deserialize(displayName);
            String miniMessageName = mm.serialize(formattedComponent);
            List<String> lines = List.of(miniMessageName);

            var ids = Main.getInstance().getHologramService().spawnTemporary(base, lines, false);
            nameHolograms.put(npc.getUuid(), ids);
        } catch (Throwable ignored) {}
    }

    private void sendNPCPackets(Player player, NPCData npcData) {
        try {
            UserProfile profileToSend = npcData.getProfile();

            if ("player".equalsIgnoreCase(npcData.getSkin())) {
                profileToSend = new UserProfile(npcData.getUuid(), npcData.getProfile().getName());
                for (com.destroystokyo.paper.profile.ProfileProperty property : player.getPlayerProfile().getProperties()) {
                    if (property.getName().equals("textures")) {
                        profileToSend.getTextureProperties().add(new TextureProperty("textures", property.getValue(), property.getSignature()));
                    }
                }
            }

            sendTeamPackets(player, npcData);

            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profileToSend, true, 0, GameMode.SURVIVAL, null, null);
            WrapperPlayServerPlayerInfoUpdate addPlayerPacket =
                    new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, playerInfo);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, addPlayerPacket);

            com.github.retrooper.packetevents.protocol.world.Location position =
                    new com.github.retrooper.packetevents.protocol.world.Location(
                            npcData.getLocation().getX(), npcData.getLocation().getY(), npcData.getLocation().getZ(),
                            npcData.getLocation().getYaw(), npcData.getLocation().getPitch());
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    npcData.getEntityId(), npcData.getUuid(), EntityTypes.PLAYER, position,
                    npcData.getLocation().getYaw(), 0, null);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);

            WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                    npcData.getEntityId(), npcData.getLocation().getYaw());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLookPacket);

            try {
                java.util.List<EntityData<?>> metadata = new java.util.ArrayList<>();
                metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0x7F));
                metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.STANDING));
                WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(npcData.getEntityId(), metadata);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
            } catch (Throwable ignored) {}

            Bukkit.getScheduler().runTaskLater(Main.getPlugin(Main.class), () -> {
                try {
                    WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(List.of(npcData.getUuid()));
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
                } catch (Exception e) {}
            }, 40L);
        } catch (Exception e) {
            System.err.println("Erro ao enviar packets do NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendTeamPackets(Player player, NPCData npcData) {
        try {
            String teamName = "npc_hide_" + npcData.getEntityId();
            String npcProfileName = npcData.getProfile().getName();

            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.empty(),
                    Component.empty(),
                    Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.NEVER,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE,
                    WrapperPlayServerTeams.OptionData.NONE
            );

            WrapperPlayServerTeams teamCreatePacket = new WrapperPlayServerTeams(
                    teamName, WrapperPlayServerTeams.TeamMode.CREATE, Optional.of(teamInfo), Collections.emptyList());

            WrapperPlayServerTeams teamAddPlayerPacket = new WrapperPlayServerTeams(
                    teamName, WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Optional.empty(), List.of(npcProfileName));

            PacketEvents.getAPI().getPlayerManager().sendPacket(player, teamCreatePacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, teamAddPlayerPacket);
        } catch (Exception e) {
            System.err.println("Falha ao enviar pacotes de time para o NPC: " + e.getMessage());
        }
    }

    public void spawnGlobal(String id, Location location, String displayName, String skinSource) {
        DisplayEntry entry = new DisplayEntry();
        entry.setId(id);
        entry.setType(DisplayEntry.Type.NPC);
        entry.setWorld(location.getWorld().getName());
        entry.setX(location.getX());
        entry.setY(location.getY());
        entry.setZ(location.getZ());
        entry.setYaw(location.getYaw());
        entry.setPitch(location.getPitch());
        entry.setMessage(displayName);
        entry.setItem(skinSource);
        configLoader.addEntry(entry);
        configLoader.save();
        reloadAll();
    }

    public void updateNpcSkin(String id, String newSkinSource) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry == null) {
            throw new IllegalArgumentException("Nenhuma entry encontrada para o ID: " + id);
        }
        entry.setItem(newSkinSource);
        configLoader.save();
        reloadAll();
    }

    public NPCData getNpcById(String id) {
        return globalNPCs.get(id);
    }

    public Set<String> getAllNpcIds() {
        return globalNPCs.keySet();
    }

    private void removeNPCFromAllPlayers(NPCData npcData) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            try {
                WrapperPlayServerTeams teamRemovePacket = new WrapperPlayServerTeams(
                        "npc_hide_" + npcData.getEntityId(), WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty(), Collections.emptyList());
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, teamRemovePacket);

                WrapperPlayServerPlayerInfoRemove removePacket =
                        new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npcData.getUuid()));
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, removePacket);

                WrapperPlayServerDestroyEntities destroyPacket =
                        new WrapperPlayServerDestroyEntities(npcData.getEntityId());
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, destroyPacket);

            } catch (Exception e) {
                System.err.println("Erro ao remover NPC para jogador " + onlinePlayer.getName() + ": " + e.getMessage());
            }
        }
    }
}