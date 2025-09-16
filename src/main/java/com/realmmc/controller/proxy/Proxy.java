package com.realmmc.controller.proxy;

import com.realmmc.controller.proxy.commands.CommandManager;
import com.realmmc.controller.proxy.listeners.ListenersManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import com.realmmc.controller.shared.storage.mongodb.MongoConfig;
import com.realmmc.controller.shared.storage.mongodb.MongoManager;
import com.realmmc.controller.shared.storage.redis.RedisConfig;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.shared.profile.ProfileSyncSubscriber;
import com.realmmc.controller.shared.utils.TaskScheduler;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.logging.Logger;

@Plugin(id = "controller", name = "Controller", description = "API controller to realmmc", version = "1.0.0", authors = {"onyell", "lucas"})
public class Proxy {

    @Getter
    private static Proxy instance;
    @Getter
    private final ProxyServer server;
    @Getter
    private final Logger logger;
    private final Path directory;
    private ProfileSyncSubscriber profileSyncSubscriber;

    @Inject
    public Proxy(ProxyServer server, Logger logger, @DataDirectory Path directory) {
        this.server = server;
        this.logger = logger;
        this.directory = directory;
        instance = this;
    }

    @Subscribe
    public void onEnable(ProxyInitializeEvent event) {
        initMongo();
        initRedis();

        TaskScheduler.init(server, this);

        profileSyncSubscriber = new ProfileSyncSubscriber();
        profileSyncSubscriber.start();

        CommandManager.registerAll(this);
        ListenersManager.registerAll(server, this);
        logger.info("Controller enabled!" + server.getPluginManager().getPlugin("controller").get().getDescription().getVersion());
    }

    @Subscribe
    public void onDisable(ProxyShutdownEvent event) {
        if (profileSyncSubscriber != null) {
            profileSyncSubscriber.stop();
            profileSyncSubscriber = null;
        }
        try { RedisManager.shutdown(); } catch (Exception ignored) {}
        try { MongoManager.shutdown(); } catch (Exception ignored) {}
        try { TaskScheduler.shutdown(); } catch (Exception ignored) {}
        logger.info("Controller disabled!" + server.getPluginManager().getPlugin("controller").get().getDescription().getVersion());
    }

    private void initMongo() {
        String uri = getProp("MONGO_URI", "mongodb://admin:admin@198.1.195.85:32017");
        String db = getProp("MONGO_DB", "controller");
        MongoManager.init(new MongoConfig(uri, db));
        logger.info("MongoDB connected to " + uri + "/" + db);
    }

    private void initRedis() {
        String host = getProp("REDIS_HOST", "198.1.195.85");
        int port = Integer.parseInt(getProp("REDIS_PORT", "30379"));
        String pass = getProp("REDIS_PASS", "redevaley@123");
        int db = Integer.parseInt(getProp("REDIS_DB", "0"));
        boolean ssl = Boolean.parseBoolean(getProp("REDIS_SSL", "false"));
        RedisManager.init(new RedisConfig(host, port, pass, db, ssl));
        logger.info("Redis connected to " + host + ":" + port + " db=" + db + (ssl ? " (ssl)" : ""));
    }

    private String getProp(String key, String def) {
        String val = System.getProperty(key);
        if (val == null || val.isEmpty()) {
            val = System.getenv(key);
        }
        return (val == null || val.isEmpty()) ? def : val;
    }
}
