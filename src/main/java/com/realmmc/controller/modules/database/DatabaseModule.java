package com.realmmc.controller.modules.database;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry; // Import ServiceRegistry
import com.realmmc.controller.shared.storage.mongodb.MongoConfig;
import com.realmmc.controller.shared.storage.mongodb.MongoManager;
import com.realmmc.controller.shared.storage.redis.RedisConfig;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber; // Import RedisSubscriber

import java.util.logging.Level; // Import Level
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class DatabaseModule extends AbstractCoreModule {

    private RedisSubscriber sharedRedisSubscriber; // Guarda a instância única

    public DatabaseModule(Logger logger) {
        super(logger);
    }

    @Override public String getName() { return "Database"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() { return "Módulo de conexão DB (Mongo/Redis) e Subscriber (v2)"; }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando conexões de banco de dados e Redis Subscriber...");

        // --- Inicialização MongoDB ---
        String mongoUri = System.getProperty("MONGO_URI", "mongodb://admin:realmmc%40mongodb@mongo-db:27017");
        String mongoDb = System.getProperty("MONGO_DB", "RealmMC-controller");
        try {
            MongoManager.init(new MongoConfig(mongoUri, mongoDb));
            logger.info("MongoDB conectado: " + mongoUri + "/" + mongoDb);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha crítica ao conectar ao MongoDB!", e);
            throw e; // Impede o carregamento se DB essencial falhar
        }

        // --- Inicialização Redis e RedisSubscriber ÚNICO ---
        String redisHost = System.getProperty("REDIS_HOST", "redis-db");
        int redisPort = Integer.parseInt(System.getProperty("REDIS_PORT", "6379"));
        String redisPassword = System.getProperty("REDIS_PASSWORD", "realmmc@redis");
        int redisDatabase = Integer.parseInt(System.getProperty("REDIS_DATABASE", "0"));
        boolean redisSsl = Boolean.parseBoolean(System.getProperty("REDIS_SSL", "false"));

        try {
            RedisConfig redisConfig = new RedisConfig(redisHost, redisPort, redisPassword.isEmpty() ? null : redisPassword, redisDatabase, redisSsl);
            RedisManager.init(redisConfig);
            logger.info("Redis conectado: " + redisHost + ":" + redisPort);

            // Cria e inicia o RedisSubscriber compartilhado
            sharedRedisSubscriber = new RedisSubscriber();
            sharedRedisSubscriber.start(); // Inicia a thread de escuta
            // Registra a instância no ServiceRegistry para outros módulos usarem
            ServiceRegistry.getInstance().registerService(RedisSubscriber.class, sharedRedisSubscriber);
            logger.info("RedisSubscriber compartilhado iniciado e registrado.");
        } catch(Exception e) {
            logger.log(Level.SEVERE, "Falha crítica ao conectar ao Redis ou iniciar RedisSubscriber!", e);
            sharedRedisSubscriber = null; // Garante que é nulo se falhar
            // Lança exceção para impedir carregamento se Redis for essencial
            throw new RuntimeException("Falha ao inicializar Redis/Subscriber.", e);
        }
    }

    @Override
    protected void onDisable() {
        logger.info("Fechando conexões de banco de dados e parando Redis Subscriber...");

        // Para o RedisSubscriber compartilhado
        if (sharedRedisSubscriber != null) {
            try {
                sharedRedisSubscriber.stop();
                ServiceRegistry.getInstance().unregisterService(RedisSubscriber.class);
                logger.info("RedisSubscriber compartilhado parado e desregistrado.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao parar RedisSubscriber compartilhado.", e);
            }
            sharedRedisSubscriber = null;
        }

        // Fecha o pool de conexões Jedis
        RedisManager.shutdown();
        logger.info("Conexão Redis (Manager) finalizada.");

        // Fecha conexão MongoDB
        MongoManager.shutdown();
        logger.info("Conexão MongoDB finalizada.");
    }

    @Override
    public int getPriority() { return 10; } // Mantém alta prioridade
}