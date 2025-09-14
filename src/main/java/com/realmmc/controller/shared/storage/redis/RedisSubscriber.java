package com.realmmc.controller.shared.storage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisSubscriber {
    private final Map<String, RedisMessageListener> listeners = new ConcurrentHashMap<>();
    private Thread thread;
    private volatile boolean running = false;

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

        thread = new Thread(() -> {
            try (Jedis jedis = RedisManager.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        RedisMessageListener l = listeners.get(channel);
                        if (l != null) l.onMessage(channel, message);
                    }
                }, channels.toArray(new String[0]));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                running = false;
            }
        }, "Redis-Subscriber");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
