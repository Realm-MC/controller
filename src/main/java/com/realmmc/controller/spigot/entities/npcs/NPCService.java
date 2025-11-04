package com.realmmc.controller.spigot.entities.npcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                            Player player = (Player) event.getPlayer();
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
                    NPCData npc = kv.getValue();
                    DisplayEntry entry = configLoader.getById(kv.getKey());

                    if (entry == null || !Boolean.TRUE.equals(entry.getIsMovible())) continue;

                    Location npcLoc = npc.location();
                    World npcWorld = npcLoc.getWorld();
                    if (npcWorld == null) continue;

                    double radius = 8.0;
                    double radiusSq = radius * radius;

                    Map<UUID, float[]> perViewer = npcViewerRot.computeIfAbsent(npc.entityId(), k -> new ConcurrentHashMap<>());

                    for (Player viewer : npcWorld.getPlayers()) {
                        if (!viewer.isValid() || viewer.isDead()) continue;

                        float targetYaw;
                        float targetPitch;

                        double dsq = viewer.getLocation().distanceSquared(npcLoc);
                        if (dsq <= radiusSq) {
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

                        float newYaw = stepAngle(current[0], targetYaw, 10.0f);
                        float newPitch = stepAngle(current[1], targetPitch, 10.0f);

                        if (Math.abs(newYaw - current[0]) > 0.1f || Math.abs(newPitch - current[1]) > 0.1f) {
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
                }
            }, 10L, 2L);
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
                NPCData npcData = createNPCData(id, location, displayName, skin, entry.getTexturesValue(), entry.getTexturesSignature());
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

    private NPCData createNPCData(String id, Location location, String displayName, String skinSource, String texturesValue, String texturesSignature) {
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

            DisplayEntry entry = configLoader.getById(entityIdToEntryId.get(npc.entityId()));
            if (entry != null && !Boolean.TRUE.equals(entry.getHologramVisible())) {
                return;
            }

            Location base = npc.location().clone().add(0, 2.0, 0);

            List<String> lines = null;
            if (entry != null) {
                if (entry.getLines() != null && !entry.getLines().isEmpty()) {
                    lines = entry.getLines();
                } else if (entry.getMessage() != null) {
                    lines = List.of(entry.getMessage());
                }
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

    public void createNpc(String id, Location location, String displayName, String skinSource) {
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
        entry.setIsMovible(false);
        entry.setHologramVisible(true);
        entry.setLines(displayName != null ? new ArrayList<>(List.of(displayName)) : new ArrayList<>());
        entry.setActions(new ArrayList<>());

        configLoader.addEntry(entry);
        configLoader.save();
        reloadAll();
    }

    public void cloneNpc(String originalId, String newId, Location location) {
        DisplayEntry originalEntry = configLoader.getById(originalId);
        if (originalEntry == null) return;

        DisplayEntry newEntry = new DisplayEntry();

        newEntry.setId(newId);
        newEntry.setType(originalEntry.getType());
        newEntry.setMessage(originalEntry.getMessage());
        newEntry.setItem(originalEntry.getItem());
        newEntry.setIsMovible(originalEntry.getIsMovible());
        newEntry.setHologramVisible(originalEntry.getHologramVisible());
        newEntry.setLines((originalEntry.getLines() != null) ? new ArrayList<>(originalEntry.getLines()) : new ArrayList<>());
        newEntry.setActions((originalEntry.getActions() != null) ? new ArrayList<>(originalEntry.getActions()) : new ArrayList<>());
        newEntry.setTexturesValue(originalEntry.getTexturesValue());
        newEntry.setTexturesSignature(originalEntry.getTexturesSignature());

        newEntry.setWorld(location.getWorld().getName());
        newEntry.setX(location.getX());
        newEntry.setY(location.getY());
        newEntry.setZ(location.getZ());
        newEntry.setYaw(location.getYaw());
        newEntry.setPitch(location.getPitch());

        configLoader.addEntry(newEntry);
        configLoader.save();
        reloadAll();
    }

    public void removeNpc(String id) {
        if (configLoader.removeEntry(id)) {
            reloadAll();
        }
    }

    public void teleportNpc(String id, Location location) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setWorld(location.getWorld().getName());
            entry.setX(location.getX());
            entry.setY(location.getY());
            entry.setZ(location.getZ());
            entry.setYaw(location.getYaw());
            entry.setPitch(location.getPitch());
            configLoader.updateEntry(entry);
            configLoader.save();
            reloadAll();
        }
    }

    public void renameNpc(String id, String newName) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setMessage(newName);
            List<String> lines = new ArrayList<>(entry.getLines());
            if (!lines.isEmpty()) {
                lines.set(0, newName);
                entry.setLines(lines);
            } else {
                lines.add(newName);
                entry.setLines(lines);
            }
            configLoader.updateEntry(entry);
            configLoader.save();
            reloadAll();
        }
    }

    public boolean toggleNameVisibility(String id) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            boolean newState = !Boolean.TRUE.equals(entry.getHologramVisible());
            entry.setHologramVisible(newState);
            configLoader.updateEntry(entry);
            configLoader.save();
            reloadAll();
            return newState;
        }
        return false;
    }

    public void updateNpcSkin(String id, String newSkinSource) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setItem(newSkinSource);
            entry.setTexturesValue(null);
            entry.setTexturesSignature(null);
            configLoader.updateEntry(entry);
            configLoader.save();
            reloadAll();
        }
    }

    public boolean toggleLookAtPlayer(String id) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            boolean newState = !Boolean.TRUE.equals(entry.getIsMovible());
            entry.setIsMovible(newState);
            configLoader.updateEntry(entry);
            configLoader.save();
            return newState;
        }
        return false;
    }

    public void addLine(String id, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = (entry.getLines() != null) ? new ArrayList<>(entry.getLines()) : new ArrayList<>();
            lines.add(text);
            entry.setLines(lines);
            configLoader.updateEntry(entry);
            configLoader.save();
            reloadAll();
        }
    }

    public boolean setLine(String id, int lineIndex, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.set(lineIndex - 1, text);
                entry.setLines(lines);
                configLoader.updateEntry(entry);
                configLoader.save();
                reloadAll();
                return true;
            }
        }
        return false;
    }

    public boolean removeLine(String id, int lineIndex) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.remove(lineIndex - 1);
                entry.setLines(lines);
                configLoader.updateEntry(entry);
                configLoader.save();
                reloadAll();
                return true;
            }
        }
        return false;
    }

    public void addAction(String id, String action) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> actions = (entry.getActions() != null) ? new ArrayList<>(entry.getActions()) : new ArrayList<>();
            actions.add(action);
            entry.setActions(actions);
            configLoader.updateEntry(entry);
            configLoader.save();
        }
    }

    public boolean removeAction(String id, int actionIndex) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> actions = entry.getActions();
            if (actions == null) return false;
            if (actionIndex > 0 && actionIndex <= actions.size()) {
                actions.remove(actionIndex - 1);
                entry.setActions(actions);
                configLoader.updateEntry(entry);
                configLoader.save();
                return true;
            }
        }
        return false;
    }

    public NPCData getNpcById(String id) {
        return globalNPCs.get(id);
    }

    public DisplayEntry getNpcEntry(String id) {
        return configLoader.getById(id);
    }

    public Set<String> getAllNpcIds() {
        return globalNPCs.keySet();
    }
}