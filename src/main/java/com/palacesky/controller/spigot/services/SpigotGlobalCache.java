package com.palacesky.controller.spigot.services;

import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisManager;
import com.palacesky.controller.shared.utils.TaskScheduler;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SpigotGlobalCache {

    private final AtomicInteger globalOnlineCount = new AtomicInteger(0);

    public SpigotGlobalCache() {
        TaskScheduler.runAsyncTimer(this::fetchGlobalCount, 1, 3, TimeUnit.SECONDS);
    }

    private void fetchGlobalCount() {
        try (Jedis jedis = RedisManager.getResource()) {
            String countStr = jedis.get(RedisChannel.GLOBAL_PLAYER_COUNT.getName());
            if (countStr != null) {
                globalOnlineCount.set(Integer.parseInt(countStr));
            } else {
                globalOnlineCount.set(Bukkit.getOnlinePlayers().size());
            }
        } catch (Exception e) {
            globalOnlineCount.set(Bukkit.getOnlinePlayers().size());
        }
    }

    public int getGlobalOnlineCount() {
        return Math.max(globalOnlineCount.get(), Bukkit.getOnlinePlayers().size());
    }
}