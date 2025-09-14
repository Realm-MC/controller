package com.realmmc.controller.shared.storage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Objects;

public final class RedisManager {
    private static JedisPool pool;
    private static RedisConfig config;

    private RedisManager() { }

    public static synchronized void init(RedisConfig cfg) {
        if (pool != null) return;
        config = Objects.requireNonNull(cfg, "RedisConfig cannot be null");
        JedisPoolConfig pc = new JedisPoolConfig();
        pc.setMaxTotal(16);
        pc.setMaxIdle(8);
        pc.setMinIdle(1);
        pc.setMaxWait(Duration.ofSeconds(10));

        if (cfg.getPassword() == null || cfg.getPassword().isEmpty()) {
            pool = new JedisPool(pc, cfg.getHost(), cfg.getPort(), 10000, null, cfg.getDatabase(), cfg.isSsl());
        } else {
            pool = new JedisPool(pc, cfg.getHost(), cfg.getPort(), 10000, cfg.getPassword(), cfg.getDatabase(), cfg.isSsl());
        }
    }

    public static Jedis getResource() {
        if (pool == null) throw new IllegalStateException("RedisManager not initialized");
        return pool.getResource();
    }

    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }
}
