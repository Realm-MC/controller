package com.realmmc.controller.shared.storage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import com.realmmc.controller.shared.utils.TaskScheduler;

public final class RedisSubscriber {
    private final Map<String, RedisMessageListener> listeners = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private CompletableFuture<Void> future;
    private JedisPubSub pubSub;

    public void registerListener(RedisChannel channel, RedisMessageListener listener) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(listener);
        listeners.put(channel.getName(), listener);
    }

    public void registerListener(String channel, RedisMessageListener listener) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(listener);
        listeners.put(channel, listener);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        Set<String> channels = listeners.keySet();
        if (channels.isEmpty()) return;

        future = TaskScheduler.runAsync(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                JedisPubSub local = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        RedisMessageListener l = listeners.get(channel);
                        if (l != null) l.onMessage(channel, message);
                    }
                };
                this.pubSub = local;
                jedis.subscribe(local, channels.toArray(new String[0]));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                running = false;
                this.pubSub = null;
            }
        });
    }

    public synchronized void stop() {
        running = false;
        try {
            if (pubSub != null) pubSub.unsubscribe();
        } catch (Exception ignored) {}
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }
}
