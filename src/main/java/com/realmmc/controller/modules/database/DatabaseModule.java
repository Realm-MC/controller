package com.realmmc.controller.modules.database;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.shared.storage.mongodb.MongoConfig;
import com.realmmc.controller.shared.storage.mongodb.MongoManager;
import com.realmmc.controller.shared.storage.redis.RedisConfig;
import com.realmmc.controller.shared.storage.redis.RedisManager;

import java.util.logging.Logger;

public class DatabaseModule extends AbstractCoreModule {

    public DatabaseModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo responsável pela conexão com MongoDB e Redis";
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando conexões de banco de dados...");

        String mongoUri = System.getProperty("MONGO_URI", "mongodb://admin:admin@198.1.195.85:32017");
        String mongoDb = System.getProperty("MONGO_DB", "controller");
        MongoManager.init(new MongoConfig(mongoUri, mongoDb));
        logger.info("MongoDB conectado: " + mongoUri + "/" + mongoDb);

        String redisHost = System.getProperty("REDIS_HOST", "198.1.195.85");
        int redisPort = Integer.parseInt(System.getProperty("REDIS_PORT", "30379"));
        String redisPassword = System.getProperty("REDIS_PASSWORD", "redevaley@123");
        int redisDatabase = Integer.parseInt(System.getProperty("REDIS_DATABASE", "0"));
        boolean redisSsl = Boolean.parseBoolean(System.getProperty("REDIS_SSL", "false"));

        RedisConfig redisConfig = new RedisConfig(redisHost, redisPort, redisPassword.isEmpty() ? null : redisPassword, redisDatabase, redisSsl);
        RedisManager.init(redisConfig);
        logger.info("Redis conectado: " + redisHost + ":" + redisPort);
    }

    @Override
    protected void onDisable() {
        logger.info("Fechando conexões de banco de dados...");
        MongoManager.shutdown();
        RedisManager.shutdown();
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }
}