package com.realmmc.controller.spigot.entities.npcs;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityMetadataProvider;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class NPCService {
    private final Map<UUID, List<NPCData>> spawnedByPlayer = new HashMap<>();
    private final NPCConfigLoader configLoader;
    private final List<NPCData> globalNPCs = new ArrayList<>();
    private final MojangSkinResolver skinResolver = new MojangSkinResolver();

    public NPCService() {
        this.configLoader = new NPCConfigLoader();
        this.configLoader.load();
        loadSavedNPCs();
    }

    private void loadSavedNPCs() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;

                Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(), 
                                               entry.getYaw(), entry.getPitch());
                
                String name = entry.getMessage();
                String skin = entry.getItem() != null ? entry.getItem() : "default";
                if (entry.getTexturesValue() != null && entry.getTexturesSignature() != null) {
                    NPCData npcData = createNPC(location, name, skin, entry.getTexturesValue(), entry.getTexturesSignature());
                    if (npcData != null) {
                        globalNPCs.add(npcData);
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            sendNPCPackets(onlinePlayer, npcData);
                        }
                    }
                } else {
                    spawnWithoutSaving(location, name, skin);
                }
                
            } catch (Exception e) {
                System.err.println("Erro ao carregar NPC ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void spawnWithoutSaving(Location location, String name, String skin) {
        try {
            NPCData npcData = createNPC(location, name, skin);
            if (npcData != null) {
                globalNPCs.add(npcData);

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    sendNPCPackets(onlinePlayer, npcData);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private NPCData createNPC(Location location, String name, String skin) {
        try {
            UUID npcUUID = UUID.randomUUID();
            int entityId = ThreadLocalRandom.current().nextInt(100000, 999999);

            UserProfile profile = new UserProfile(npcUUID, name);

            if (!"default".equalsIgnoreCase(skin)) {
                try {
                    TextureProperty tp = skinResolver.resolveByNameOrUuid(skin);
                    if (tp != null) {
                        profile.getTextureProperties().add(tp);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao obter skin: " + e.getMessage());
                }
            }

            return new NPCData(npcUUID, entityId, profile, location, name, skin);
            
        } catch (Exception e) {
            System.err.println("Erro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private NPCData createNPC(Location location, String name, String skin, String texturesValue, String texturesSignature) {
        try {
            UUID npcUUID = UUID.randomUUID();
            int entityId = ThreadLocalRandom.current().nextInt(100000, 999999);

            UserProfile profile = new UserProfile(npcUUID, name);
            if (texturesValue != null && texturesSignature != null) {
                profile.getTextureProperties().add(new TextureProperty("textures", texturesValue, texturesSignature));
            }
            return new NPCData(npcUUID, entityId, profile, location, name, skin);

        } catch (Exception e) {
            System.err.println("Erro ao criar NPC (textures explícitas): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void sendNPCPackets(Player player, NPCData npcData) {
        try {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = 
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    npcData.getProfile(),
                    true,
                    0,
                    GameMode.SURVIVAL,
                    null,
                    null
                );
            
            WrapperPlayServerPlayerInfoUpdate addPlayerPacket = 
                new WrapperPlayServerPlayerInfoUpdate(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    playerInfo
                );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, addPlayerPacket);

            com.github.retrooper.packetevents.protocol.world.Location position =
                new com.github.retrooper.packetevents.protocol.world.Location(
                    npcData.getLocation().getX(),
                    npcData.getLocation().getY(),
                    npcData.getLocation().getZ(),
                    npcData.getLocation().getYaw(),
                    npcData.getLocation().getPitch()
                );
            
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                npcData.getEntityId(),
                npcData.getUuid(),
                EntityTypes.PLAYER,
                position,
                npcData.getLocation().getYaw(),
                0,
                null
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);

            WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                npcData.getEntityId(),
                npcData.getLocation().getYaw()
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLookPacket);

            try {
                List<EntityData> metadata = new ArrayList<>();
                metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0x7F));
                metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.STANDING));
                WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(npcData.getEntityId(), (EntityMetadataProvider) metadata);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, metaPacket);
            } catch (Throwable ignored) {}

            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugins()[0], () -> {
                try {
                    WrapperPlayServerPlayerInfoRemove removePacket = 
                        new WrapperPlayServerPlayerInfoRemove(Arrays.asList(npcData.getUuid()));
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
                } catch (Exception e) {
                }
            }, 40L);
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar packets do NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void spawn(Player player, Location location, String name) {
        spawn(player, location, name, player.getName());
    }

    public void spawn(Player player, Location location, String name, String skin) {
        try {
            NPCData npcData = createNPC(location, name, skin);
            if (npcData == null) {
                player.sendMessage("§cErro ao criar NPC!");
                return;
            }

            spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(npcData);

            globalNPCs.add(npcData);

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                sendNPCPackets(onlinePlayer, npcData);
            }

            DisplayEntry entry = new DisplayEntry();
            entry.setType(DisplayEntry.Type.NPC);
            entry.setWorld(location.getWorld().getName());
            entry.setX(location.getX());
            entry.setY(location.getY());
            entry.setZ(location.getZ());
            entry.setYaw(location.getYaw());
            entry.setPitch(location.getPitch());
            entry.setMessage(name);
            entry.setItem(skin);
            
            configLoader.addEntry(entry);
            configLoader.save();

            player.sendMessage("§aNPC '" + name + "' criado com sucesso!");
            
        } catch (Exception e) {
            player.sendMessage("§cErro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void spawnGlobal(Location location, String name, String skin) {
        DisplayEntry entry = new DisplayEntry();
        entry.setType(DisplayEntry.Type.NPC);
        entry.setWorld(location.getWorld().getName());
        entry.setX(location.getX());
        entry.setY(location.getY());
        entry.setZ(location.getZ());
        entry.setYaw(location.getYaw());
        entry.setPitch(location.getPitch());
        entry.setMessage(name);
        entry.setItem(skin);

        configLoader.addEntry(entry);
        configLoader.save();

        spawnWithoutSaving(location, name, skin);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage("§aNPC global '" + name + "' foi criado!");
        }
    }

    public void remove(Player player) {
        List<NPCData> entities = spawnedByPlayer.get(player.getUniqueId());
        if (entities != null) {
            for (NPCData npcData : entities) {
                removeNPCFromAllPlayers(npcData);
                globalNPCs.remove(npcData);
            }
            entities.clear();
            player.sendMessage("§aTodos os seus NPCs foram removidos!");
        }
    }

    private void removeNPCFromAllPlayers(NPCData npcData) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            try {
                WrapperPlayServerPlayerInfoRemove removePacket = 
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(npcData.getUuid()));
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, removePacket);

                WrapperPlayServerDestroyEntities destroyPacket = 
                    new WrapperPlayServerDestroyEntities(npcData.getEntityId());
                PacketEvents.getAPI().getPlayerManager().sendPacket(onlinePlayer, destroyPacket);
                
            } catch (Exception e) {
                System.err.println("Erro ao remover NPC para jogador " + onlinePlayer.getName() + ": " + e.getMessage());
            }
        }
    }

    public void clearAll() {
        for (NPCData npcData : globalNPCs) {
            removeNPCFromAllPlayers(npcData);
        }
        globalNPCs.clear();

        for (Map.Entry<UUID, List<NPCData>> entry : spawnedByPlayer.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage("§eTodos os NPCs foram limpos!");
            }
            entry.getValue().clear();
        }

        configLoader.clearEntries();
        configLoader.save();
    }

    public void clearAll(Player player) {
        try {
            List<NPCData> playerNPCs = spawnedByPlayer.get(player.getUniqueId());
            if (playerNPCs != null) {
                for (NPCData npcData : playerNPCs) {
                    removeNPCFromAllPlayers(npcData);
                    globalNPCs.remove(npcData);
                }
                playerNPCs.clear();
            }

            configLoader.clearEntries();
            configLoader.save();
            
            player.sendMessage("§aTodos os NPCs foram removidos!");
            
        } catch (Exception e) {
            player.sendMessage("§cErro ao remover NPCs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reload() {
        clearAll();
        configLoader.load();
        loadSavedNPCs();
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage("§aNPCs recarregados!");
        }
    }

    public void sendNPCsToPlayer(Player player) {
        for (NPCData npcData : globalNPCs) {
            sendNPCPackets(player, npcData);
        }
    }

    public void onPlayerJoin(Player player) {
        for (NPCData npcData : globalNPCs) {
            sendNPCPackets(player, npcData);
        }
    }

    public void listNPCs(Player player) {
        if (globalNPCs.isEmpty()) {
            player.sendMessage("§eNenhum NPC global encontrado.");
        } else {
            player.sendMessage("§aNPCs globais (" + globalNPCs.size() + "):");
            for (NPCData npcData : globalNPCs) {
                try {
                    Location loc = npcData.getLocation();
                    player.sendMessage("§7- " + npcData.getName() + " §8(" + 
                        loc.getWorld().getName() + " " + 
                        (int)loc.getX() + " " + (int)loc.getY() + " " + (int)loc.getZ() + ")");
                } catch (Exception e) {
                    player.sendMessage("§7- NPC (erro ao obter informações)");
                }
            }
        }
        
        List<NPCData> playerNPCs = spawnedByPlayer.get(player.getUniqueId());
        if (playerNPCs != null && !playerNPCs.isEmpty()) {
            player.sendMessage("§aSeus NPCs (" + playerNPCs.size() + ")");
        }
    }


}