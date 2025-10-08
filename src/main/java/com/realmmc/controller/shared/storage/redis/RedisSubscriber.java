package com.realmmc.controller.shared.storage.redis;

import com.realmmc.controller.shared.utils.TaskScheduler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisSubscriber {
    private final Map<String, RedisMessageListener> listeners = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private CompletableFuture<Void> future;
    private JedisPubSub pubSub;

    private ScheduledFuture<?> pingTask;

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
                pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        RedisMessageListener l = listeners.get(channel);
                        if (l != null) l.onMessage(channel, message);
                    }
                };

                startPingTask(pubSub);

                jedis.subscribe(pubSub, channels.toArray(new String[0]));
            } catch (Exception e) {
            } finally {
                stopPingTask();
                running = false;
                this.pubSub = null;
            }
        });
    }

    public synchronized void stop() {
        running = false;
        stopPingTask();
        try {
            if (pubSub != null) pubSub.unsubscribe();
        } catch (Exception ignored) {
        }
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }

    private void startPingTask(JedisPubSub pubSubInstance) {
        if (pingTask != null && !pingTask.isDone()) {
            return;
        }
        pingTask = TaskScheduler.runAsyncTimer(() -> {
            try {
                if (pubSubInstance != null && pubSubInstance.isSubscribed()) {
                    pubSubInstance.ping();
                }
            } catch (Exception e) {
                stopPingTask();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void stopPingTask() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }
}