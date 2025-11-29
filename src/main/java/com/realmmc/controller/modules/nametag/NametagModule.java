package com.realmmc.controller.modules.nametag;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.services.NametagService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class NametagModule extends AbstractCoreModule {

    private NametagService nametagService;

    public NametagModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "NametagModule";
    }

    @Override
    public String getVersion() {
        return "1.1";
    }

    @Override
    public String getDescription() {
        return "Gere as tags e tablist em servidores padrão";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"SpigotModule"};
    }

    @Override
    protected void onEnable() {
        nametagService = new NametagService();
        ServiceRegistry.getInstance().registerService(NametagService.class, nametagService);

        Bukkit.getPluginManager().registerEvents(nametagService, Main.getInstance());

        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> {
                    sub.registerListener(RedisChannel.PROFILES_SYNC, nametagService);
                    sub.registerListener(RedisChannel.COSMETICS_SYNC, nametagService);
                });

        for (Player p : Bukkit.getOnlinePlayers()) {
            nametagService.updateTag(p);
        }
        logger.info("NametagModule ativado (Serviço padrão de Tags).");
    }

    @Override
    protected void onDisable() {
        if (nametagService != null) {
            HandlerList.unregisterAll(nametagService);

            ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                    .ifPresent(sub -> {
                        sub.unregisterListener(RedisChannel.PROFILES_SYNC);
                        sub.unregisterListener(RedisChannel.COSMETICS_SYNC);
                    });

            ServiceRegistry.getInstance().unregisterService(NametagService.class);
            logger.info("NametagModule desativado.");
        }
        nametagService = null;
    }
}