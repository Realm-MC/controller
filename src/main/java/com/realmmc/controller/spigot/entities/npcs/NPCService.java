package com.realmmc.controller.spigot.entities.npcs;

import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.lang.reflect.*;

public class NPCService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final NPCConfigLoader configLoader;
    private final List<FakePlayer> globalNPCs = new ArrayList<>();

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

                spawnWithoutSaving(location, name, skin);
                
            } catch (Exception e) {
                System.err.println("Erro ao carregar NPC ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void spawnWithoutSaving(Location location, String name, String skin) {
        try {
            FakePlayer fakePlayer = createFakePlayer(location, name, skin);
            globalNPCs.add(fakePlayer);

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                sendNPCPackets(onlinePlayer, fakePlayer);
            }
        } catch (Exception e) {
            System.err.println("Erro ao criar NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private FakePlayer createFakePlayer(Location location, String name, String skin) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object gameProfile = gameProfileConstructor.newInstance(UUID.randomUUID(), name);

            if (!"default".equals(skin)) {
                try {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(skin);
                    if (offlinePlayer.hasPlayedBefore()) {
                        Player onlinePlayer = Bukkit.getPlayer(skin);
                        if (onlinePlayer != null) {
                            Method getProfileMethod = onlinePlayer.getClass().getMethod("getProfile");
                            Object playerProfile = getProfileMethod.invoke(onlinePlayer);
                            
                            Method getPropertiesMethod = playerProfile.getClass().getMethod("getProperties");
                            Object properties = getPropertiesMethod.invoke(playerProfile);
                            
                            Method containsKeyMethod = properties.getClass().getMethod("containsKey", Object.class);
                            boolean hasTextures = (Boolean) containsKeyMethod.invoke(properties, "textures");
                            
                            if (hasTextures) {
                                Method getMethod = properties.getClass().getMethod("get", Object.class);
                                Object textureCollection = getMethod.invoke(properties, "textures");
                                
                                Method iteratorMethod = textureCollection.getClass().getMethod("iterator");
                                Iterator<?> iterator = (Iterator<?>) iteratorMethod.invoke(textureCollection);
                                
                                if (iterator.hasNext()) {
                                    Object textureProperty = iterator.next();
                                    
                                    Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
                                    putMethod.invoke(getPropertiesMethod.invoke(gameProfile), "textures", textureProperty);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao obter skin: " + e.getMessage());
                }
            }

            return new FakePlayer(gameProfile, location, name, skin);
        } catch (Exception e) {
            System.err.println("Erro ao criar FakePlayer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void sendNPCPackets(Player player, FakePlayer fakePlayer) {
        try {
            Object craftPlayer = player.getClass().cast(player);
            Method getHandleMethod = craftPlayer.getClass().getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(craftPlayer);
            
            Field connectionField = entityPlayer.getClass().getField("connection");
            Object connection = connectionField.get(entityPlayer);

            // TODO: necessário usar NMS packets
            System.out.println("NPC " + fakePlayer.getName() + " criado em " + fakePlayer.getLocation());
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar packets do NPC: " + e.getMessage());
        }
    }

    public void spawn(Player player, Location location, String name) {
        spawn(player, location, name, player.getName());
    }

    public void spawn(Player player, Location location, String name, String skin) {
        try {
            FakePlayer fakePlayer = createFakePlayer(location, name, skin);
            if (fakePlayer != null) {
                List<UUID> entities = spawnedByPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
                entities.add(fakePlayer.getUuid());

                sendNPCPackets(player, fakePlayer);
                
                player.sendMessage("§aNPC '" + name + "' criado com sucesso!");
            }
        } catch (Exception e) {
            System.err.println("Erro ao criar NPC temporário: " + e.getMessage());
            player.sendMessage("§cErro ao criar NPC: " + e.getMessage());
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
        List<UUID> entities = spawnedByPlayer.get(player.getUniqueId());
        if (entities != null) {
            for (UUID entityId : entities) {
                removeNPCForPlayer(player, entityId);
            }
            entities.clear();
            player.sendMessage("§aTodos os seus NPCs foram removidos!");
        }
    }

    private void removeNPCForPlayer(Player player, UUID npcId) {
        try {
            globalNPCs.removeIf(fakePlayer -> fakePlayer.getUuid().equals(npcId));
            player.sendMessage("§eNPC removido!");
        } catch (Exception e) {
            System.err.println("Erro ao remover NPC: " + e.getMessage());
        }
    }

    public void clearAll() {
        globalNPCs.clear();

        for (Map.Entry<UUID, List<UUID>> entry : spawnedByPlayer.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage("§eTodos os NPCs foram limpos!");
            }
            entry.getValue().clear();
        }

        configLoader.clearEntries();
        configLoader.save();
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
        for (FakePlayer fakePlayer : globalNPCs) {
            sendNPCPackets(player, fakePlayer);
        }
    }

    public void listNPCs(Player player) {
        if (globalNPCs.isEmpty()) {
            player.sendMessage("§eNenhum NPC global encontrado.");
        } else {
            player.sendMessage("§aNPCs globais (" + globalNPCs.size() + "):");
            for (FakePlayer fakePlayer : globalNPCs) {
                Location loc = fakePlayer.getLocation();
                player.sendMessage("§7- " + fakePlayer.getName() + " §8(" + 
                    loc.getWorld().getName() + " " + 
                    (int)loc.getX() + " " + (int)loc.getY() + " " + (int)loc.getZ() + ")");
            }
        }
        
        List<UUID> playerNPCs = spawnedByPlayer.get(player.getUniqueId());
        if (playerNPCs != null && !playerNPCs.isEmpty()) {
            player.sendMessage("§aSeus NPCs (" + playerNPCs.size() + ")");
        }
    }

    private static class FakePlayer {
        private final Object gameProfile;
        private final Location location;
        private final String name;
        private final String skin;
        private final UUID uuid;

        public FakePlayer(Object gameProfile, Location location, String name, String skin) {
            this.gameProfile = gameProfile;
            this.location = location;
            this.name = name;
            this.skin = skin;
            this.uuid = UUID.randomUUID();
        }

        public Object getGameProfile() {
            return gameProfile;
        }

        public UUID getUuid() {
            return uuid;
        }

        public Location getLocation() {
            return location;
        }

        public String getName() {
            return name;
        }

        public String getSkin() {
            return skin;
        }
    }
}