package com.realmmc.controller.spigot.entities.npcs;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.actions.Actions;
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
import java.util.logging.Level;

public class NPCService implements Listener {
    private final NPCConfigLoader configLoader;
    private final Map<String, NPCData> globalNPCs = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityIdToEntryId = new ConcurrentHashMap<>();
    private final Map<String, Long> clickDebounce = new ConcurrentHashMap<>();
    private PacketListenerAbstract interactListener;
    private final MojangSkinResolver mojangResolver = new MojangSkinResolver();
    private final MineskinResolver mineskinResolver = new MineskinResolver();
    private final Map<UUID, List<UUID>> nameHolograms = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final Map<Integer, Map<UUID, float[]>> npcViewerRot = new ConcurrentHashMap<>();

    public NPCService() {
        this.configLoader = new NPCConfigLoader();
        this.configLoader.load();
        loadSavedNPCs();
        startLookTask();
        try {
            if (interactListener == null) {
                interactListener = new PacketListenerAbstract() {
                    @Override
                    public void onPacketReceive(PacketReceiveEvent event) {
                        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                            int targetId = wrapper.getEntityId();
                            String entryId = entityIdToEntryId.get(targetId);
                            if (entryId == null) return;
                            Player player = event.getPlayer();
                            String actionName = String.valueOf(wrapper.getAction());
                            if ("ATTACK".equals(actionName)) return;
                            String handName = String.valueOf(wrapper.getHand());
                            if (handName != null && handName.equals("OFF_HAND")) return;

                            long now = System.currentTimeMillis();
                            String key = player.getUniqueId() + ":" + targetId;
                            Long last = clickDebounce.get(key);
                            if (last != null && now - last < 300) return;
                            clickDebounce.put(key, now);

                            DisplayEntry entry = configLoader.getById(entryId);
                            if (entry == null || entry.getActions() == null || entry.getActions().isEmpty()) return;
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                                Actions.runAll(player, entry, player.getLocation(), entry.getActions());
                            });
                        }
                    }
                };
                PacketEvents.getAPI().getEventManager().registerListener(interactListener);
            }
        } catch (Throwable t) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Falha crítica ao registrar listener de interação de pacotes para NPCs.", t);
        }
    }

    private void startLookTask() {
        try {
            Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
                if (globalNPCs.isEmpty()) return;
                for (Map.Entry<String, NPCData> kv : globalNPCs.entrySet()) {
                    String id = kv.getKey();
                    NPCData npc = kv.getValue();
                    DisplayEntry entry = configLoader.getById(id);
                    if (entry == null || !Boolean.TRUE.equals(entry.getIsMovible())) continue;
                    double radius = 6.0;
                    Location npcLoc = npc.location();

                    Map<UUID, float[]> perViewer = npcViewerRot.computeIfAbsent(npc.entityId(), k -> new ConcurrentHashMap<>());

                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (!viewer.isValid() || viewer.isDead()) continue;
                        if (!viewer.getWorld().equals(npcLoc.getWorld())) continue;

                        float targetYaw;
                        float targetPitch;
                        double dsq = viewer.getLocation().distanceSquared(npcLoc);
                        if (dsq <= radius * radius) {
                            Location eye = viewer.getEyeLocation();
                            double dx = eye.getX() - npcLoc.getX();
                            double dy = eye.getY() - (npcLoc.getY() + 1.50);
                            double dz = eye.getZ() - npcLoc.getZ();
                            double yawRad = Math.atan2(-dx, dz);
                            double xz = Math.max(0.0001, Math.sqrt(dx * dx + dz * dz));
                            double pitchRad = -Math.atan2(dy, xz);
                            targetYaw = normalizeYaw((float) Math.toDegrees(yawRad));
                            targetPitch = clampPitch((float) Math.toDegrees(pitchRad));
                        } else {
                            targetYaw = normalizeYaw(npcLoc.getYaw());
                            targetPitch = clampPitch(npcLoc.getPitch());
                        }

                        float[] current = perViewer.computeIfAbsent(viewer.getUniqueId(), u -> new float[]{normalizeYaw(npcLoc.getYaw()), clampPitch(npcLoc.getPitch())});
                        float newYaw = stepAngle(current[0], targetYaw, 5.0f);
                        float newPitch = stepAngle(current[1], targetPitch, 5.0f);
                        current[0] = newYaw;
                        current[1] = newPitch;

                        try {
                            WrapperPlayServerEntityHeadLook head = new WrapperPlayServerEntityHeadLook(npc.entityId(), newYaw);
                            WrapperPlayServerEntityRotation rotation = new WrapperPlayServerEntityRotation(npc.entityId(), newYaw, newPitch, false);
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, head);
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, rotation);
                        } catch (Throwable t) {
                            Main.getInstance().getLogger().log(Level.WARNING, "Erro não crítico ao enviar pacotes de rotação de NPC.", t);
                        }
                    }
                }
            }, 10L, 1L);
        } catch (Throwable t) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Falha crítica ao iniciar a tarefa de rotação de NPCs.", t);
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw >= 180.0F) yaw -= 360.0F;
        if (yaw < -180.0F) yaw += 360.0F;
        return yaw;
    }

    private float clampPitch(float pitch) {
        if (pitch > 89.9f) return 89.9f;
        if (pitch < -89.9f) return -89.9f;
        return pitch;
    }

    private float stepAngle(float current, float target, float maxStep) {
        float delta = normalizeYaw(target - current);
        if (Math.abs(delta) > maxStep) delta = (delta > 0 ? maxStep : -maxStep);
        return normalizeYaw(current + delta);
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        resendAllTo(event.getPlayer());
    }

    public void resendAllTo(Player player) {
        for (NPCData npc : globalNPCs.values()) {
            try {
                sendNPCPackets(player, npc);
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.WARNING, "Falha ao enviar pacotes do NPC " + npc.entityId() + " para o jogador " + player.getName(), e);
            }
        }
    }

    public void despawnAllFor(Player player) {
        if (globalNPCs.isEmpty()) return;
        int[] ids = globalNPCs.values().stream().mapToInt(NPCData::entityId).toArray();
        try {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(ids);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Falha ao enviar pacote de destruição de NPC para " + player.getName(), e);
        }
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
        } catch (Throwable t) {
            Main.getInstance().getLogger().log(Level.WARNING, "Erro não crítico ao remover hologramas de nomes de NPCs.", t);
        }
        nameHolograms.clear();
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            for (NPCData npc : globalNPCs.values()) {
                Team t = sb.getTeam("npc_hide_" + npc.entityId());
                if (t != null) t.unregister();
            }
        } catch (Throwable t) {
            Main.getInstance().getLogger().log(Level.WARNING, "Erro não crítico ao remover times de scoreboard de NPCs.", t);
        }
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

    public boolean isNameHologram(UUID hologramUuid) {
        for (List<UUID> ids : nameHolograms.values()) {
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
                    entityIdToEntryId.put(npcData.entityId(), id);
                    spawnNameHologram(npcData);
                }
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Erro ao carregar NPC com ID de config " + entry.getId(), e);
            }
        }
    }

    private NPCData createNPC(String id, Location location, String displayName, String skinSource, String texturesValue, String texturesSignature) {
        try {
            UUID npcUUID = UUID.randomUUID();
            int entityId = ThreadLocalRandom.current().nextInt(100000, 999999);
            String internalName = (id != null && !id.isEmpty()) ? id : "NPC_" + entityId;
            UserProfile profile = new UserProfile(npcUUID, internalName);

            if (texturesValue != null && texturesSignature != null) {
                profile.getTextureProperties().add(new TextureProperty("textures", texturesValue, texturesSignature));
            } else if (!"player".equalsIgnoreCase(skinSource)) {
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
            Main.getInstance().getLogger().log(Level.SEVERE, "Erro ao criar dados do NPC: " + id, e);
            return null;
        }
    }

    private void spawnNameHologram(NPCData npc) {
        try {
            List<UUID> old = nameHolograms.remove(npc.uuid());
            if (old != null) {
                Main.getInstance().getHologramService().removeByUUIDs(old);
            }
            Location base = npc.location().clone().add(0, 2.0, 0);

            List<String> lines = null;
            try {
                String entryId = entityIdToEntryId.get(npc.entityId());
                if (entryId != null) {
                    DisplayEntry entry = configLoader.getById(entryId);
                    if (entry != null) {
                        if (entry.getLines() != null && !entry.getLines().isEmpty()) {
                            lines = entry.getLines();
                        } else if (entry.getMessage() != null) {
                            Component formattedComponent = legacySerializer.deserialize(entry.getMessage());
                            String mmName = mm.serialize(formattedComponent);
                            lines = List.of(mmName);
                        }
                    }
                }
            } catch (Throwable t) {
                Main.getInstance().getLogger().log(Level.WARNING, "Erro não crítico ao processar linhas do holograma para NPC " + npc.entityId(), t);
            }
            if (lines == null || lines.isEmpty()) {
                return;
            }

            var ids = Main.getInstance().getHologramService().spawnTemporary(base, lines, false);
            try { Main.getInstance().getHologramService().addTagToUUIDs(ids, "controller_npc_name_line"); } catch (Throwable t) {
                Main.getInstance().getLogger().log(Level.WARNING, "Erro ao adicionar tag a UUIDs de holograma de NPC.", t);
            }
            nameHolograms.put(npc.uuid(), ids);
        } catch (Throwable t) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Falha ao criar holograma de nome para NPC " + npc.uuid(), t);
        }
    }

    private void sendNPCPackets(Player player, NPCData npcData) {
        try {
            UserProfile profileToSend = npcData.profile();

            if ("player".equalsIgnoreCase(npcData.skin())) {
                profileToSend = new UserProfile(npcData.uuid(), npcData.profile().getName());
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
                            npcData.location().getX(), npcData.location().getY(), npcData.location().getZ(),
                            npcData.location().getYaw(), npcData.location().getPitch());
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    npcData.entityId(), npcData.uuid(), EntityTypes.PLAYER, position,
                    npcData.location().getYaw(), 0, null);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);

            WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                    npcData.entityId(), npcData.location().getYaw());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLookPacket);

            try {
                java.util.List<EntityData<?>> metadata = new java.util.ArrayList<>();
                metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0x7F));
                metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.STANDING));
                WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(npcData.entityId(), metadata);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
            } catch (Throwable t) {
                Main.getInstance().getLogger().log(Level.WARNING, "Falha ao enviar metadados de entidade para NPC.", t);
            }

            Bukkit.getScheduler().runTaskLater(Main.getPlugin(Main.class), () -> {
                try {
                    WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(List.of(npcData.uuid()));
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
                } catch (Exception e) {
                    Main.getInstance().getLogger().log(Level.WARNING, "Falha ao remover NPC da lista de jogadores (tablist) para " + player.getName(), e);
                }
            }, 40L);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Erro ao enviar pacotes do NPC: " + npcData.entityId(), e);
        }
    }

    private void sendTeamPackets(Player player, NPCData npcData) {
        try {
            String teamName = "npc_hide_" + npcData.entityId();
            String npcProfileName = npcData.profile().getName();

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
            Main.getInstance().getLogger().log(Level.SEVERE, "Falha ao enviar pacotes de time para o NPC: " + npcData.entityId(), e);
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
        if (entry.getLines() == null || entry.getLines().isEmpty()) {
            entry.setLines(List.of("npc"));
        }
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

    public void updateNpcTextures(String id, String texturesValue, String texturesSignature) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry == null) {
            throw new IllegalArgumentException("Nenhuma entry encontrada para o ID: " + id);
        }
        entry.setTexturesValue(texturesValue);
        entry.setTexturesSignature(texturesSignature);
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
                        "npc_hide_" + npcData.entityId(), WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty(), Collections.emptyList());
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, teamRemovePacket);

                WrapperPlayServerPlayerInfoRemove removePacket =
                        new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npcData.uuid()));
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, removePacket);

                WrapperPlayServerDestroyEntities destroyPacket =
                        new WrapperPlayServerDestroyEntities(npcData.entityId());
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, destroyPacket);

            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.WARNING, "Erro ao remover NPC para jogador " + onlinePlayer.getName(), e);
            }
        }
    }
}