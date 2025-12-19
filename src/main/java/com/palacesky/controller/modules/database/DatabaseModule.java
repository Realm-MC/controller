package com.palacesky.controller.modules.database;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.storage.mongodb.MongoConfig;
import com.palacesky.controller.shared.storage.mongodb.MongoManager;
import com.palacesky.controller.shared.storage.redis.RedisConfig;
import com.palacesky.controller.shared.storage.redis.RedisManager;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;

import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class DatabaseModule extends AbstractCoreModule {

    private RedisSubscriber sharedRedisSubscriber;

    public DatabaseModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public String getVersion() {
        return "1.1.1";
    }

    @Override
    public String getDescription() {
        return "Módulo de conexão DB (Mongo/Redis) com Retry Logic";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SchedulerModule"};
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando conexões de banco de dados...");

        String mongoUri = System.getProperty("MONGO_URI", "mongodb://admin:realmmc%40mongodb@mongo-db:27017");
        String mongoDb = System.getProperty("MONGO_DB", "RealmMC-controller");
        String redisHost = System.getProperty("REDIS_HOST", "redis-db");
        int redisPort = Integer.parseInt(System.getProperty("REDIS_PORT", "6379"));
        String redisPassword = System.getProperty("REDIS_PASSWORD", "realmmc@redis");
        int redisDatabase = Integer.parseInt(System.getProperty("REDIS_DATABASE", "0"));
        boolean redisSsl = Boolean.parseBoolean(System.getProperty("REDIS_SSL", "false"));

        int maxRetries = 10;
        long sleepTime = 3000L;

        boolean mongoConnected = false;
        for (int i = 0; i < maxRetries; i++) {
            try {
                MongoManager.init(new MongoConfig(mongoUri, mongoDb));
                MongoManager.db().runCommand(new org.bson.Document("ping", 1));

                logger.info("MongoDB conectado com sucesso! (Tentativa " + (i + 1) + ")");
                mongoConnected = true;
                break;
            } catch (Exception e) {
                if (i < maxRetries - 1) {
                    logger.warning("MongoDB indisponível. Tentando novamente em 3s... (" + (i + 1) + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    logger.log(Level.SEVERE, "Falha crítica: Não foi possível conectar ao MongoDB após " + maxRetries + " tentativas.", e);
                    throw e;
                }
            }
        }

        if (!mongoConnected) throw new RuntimeException("Abortando inicialização: MongoDB offline.");

        boolean redisConnected = false;
        for (int i = 0; i < maxRetries; i++) {
            try {
                RedisConfig redisConfig = new RedisConfig(redisHost, redisPort, redisPassword.isEmpty() ? null : redisPassword, redisDatabase, redisSsl);
                RedisManager.init(redisConfig);

                try (var jedis = RedisManager.getResource()) {
                    String response = jedis.ping();
                    if (!"PONG".equals(response)) throw new RuntimeException("Redis ping failed");
                }

                sharedRedisSubscriber = new RedisSubscriber();
                sharedRedisSubscriber.start();
                ServiceRegistry.getInstance().registerService(RedisSubscriber.class, sharedRedisSubscriber);

                logger.info("Redis conectado com sucesso! (Tentativa " + (i + 1) + ")");
                redisConnected = true;
                break;
            } catch (Exception e) {
                RedisManager.shutdown();
                if (i < maxRetries - 1) {
                    logger.warning("Redis indisponível. Tentando novamente em 3s... (" + (i + 1) + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    logger.log(Level.SEVERE, "Falha crítica: Não foi possível conectar ao Redis após " + maxRetries + " tentativas.", e);
                    throw e;
                }
            }
        }

        if (!redisConnected) throw new RuntimeException("Abortando inicialização: Redis offline.");
    }

    @Override
    protected void onDisable() {
        logger.info("Fechando conexões de banco de dados...");

        if (sharedRedisSubscriber != null) {
            try {
                sharedRedisSubscriber.stop();
                ServiceRegistry.getInstance().unregisterService(RedisSubscriber.class);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar RedisSubscriber.", e);
            }
            sharedRedisSubscriber = null;
        }

        RedisManager.shutdown();
        logger.info("Conexão Redis finalizada.");

        MongoManager.shutdown();
        logger.info("Conexão MongoDB finalizada.");
    }
}