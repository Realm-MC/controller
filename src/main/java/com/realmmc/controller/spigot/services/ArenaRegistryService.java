package com.realmmc.controller.spigot.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.GameState;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.shared.storage.redis.packet.ArenaHeartbeatPacket;
import com.realmmc.controller.shared.utils.TaskScheduler;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArenaRegistryService implements RedisMessageListener {

    private final Map<String, ArenaHeartbeatPacket> arenas = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public ArenaRegistryService() {
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.ARENA_HEARTBEAT, this));

        TaskScheduler.runAsyncTimer(this::cleanupStaleArenas, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.ARENA_HEARTBEAT.getName().equals(channel)) return;

        try {
            ArenaHeartbeatPacket packet = mapper.readValue(message, ArenaHeartbeatPacket.class);
            arenas.put(packet.getArenaId(), packet);
            lastHeartbeat.put(packet.getArenaId(), System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupStaleArenas() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastHeartbeat.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > 5000) {
                arenas.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public List<ArenaHeartbeatPacket> getArenas(String gameType) {
        return arenas.values().stream()
                .filter(a -> a.getGameType().equalsIgnoreCase(gameType))
                .sorted(Comparator.comparing(ArenaHeartbeatPacket::getArenaId))
                .collect(Collectors.toList());
    }

    public Optional<ArenaHeartbeatPacket> getArena(String arenaId) {
        return Optional.ofNullable(arenas.get(arenaId));
    }

    public Optional<ArenaHeartbeatPacket> findBestArena(String gameType) {
        return arenas.values().stream()
                .filter(a -> a.getGameType().equalsIgnoreCase(gameType))
                .filter(a -> a.getState() == GameState.WAITING)
                .filter(a -> a.getCurrentPlayers() < a.getMaxPlayers())
                .max(Comparator.comparingInt(ArenaHeartbeatPacket::getCurrentPlayers));
    }
}