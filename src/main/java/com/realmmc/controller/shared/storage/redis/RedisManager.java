package com.realmmc.controller.shared.storage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Objects;

public final class RedisManager {
    private static JedisPool pool;
    private static RedisConfig config;

    private RedisManager() {
    }

    public static synchronized void init(RedisConfig cfg) {
        if (pool != null) return;
        config = Objects.requireNonNull(cfg, "RedisConfig cannot be null");
        JedisPoolConfig pc = new JedisPoolConfig();
        pc.setMaxTotal(16);
        pc.setMaxIdle(8);
        pc.setMinIdle(1);
        pc.setMaxWait(Duration.ofSeconds(10));

        if (cfg.password() == null || cfg.password().isEmpty()) {
            pool = new JedisPool(pc, cfg.host(), cfg.port(), 10000, null, cfg.database(), cfg.ssl());
        } else {
            pool = new JedisPool(pc, cfg.host(), cfg.port(), 10000, cfg.password(), cfg.database(), cfg.ssl());
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
