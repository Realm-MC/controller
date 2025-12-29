package com.palacesky.controller.modules.chat;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.chat.channels.LocalChannel;
import com.palacesky.controller.shared.chat.ChatService;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class SpigotChatModule extends AbstractCoreModule {

    private ChatService chatService;
    private BukkitAudiences adventure;

    public SpigotChatModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() { return "SpigotChat"; }

    @Override
    public String getVersion() { return "2.0.0"; }

    @Override
    public String getDescription() { return "Gerencia o Chat Local e Canais via Framework"; }

    @Override
    public String[] getDependencies() { return new String[]{"Database", "Profile"}; }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[Chat] Inicializando Framework de Chat no Spigot...");

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());

        this.adventure = BukkitAudiences.create(plugin);

        ChatService.PlatformChatAdapter spigotAdapter = (message, permission, channel) -> {

            if (permission != null) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission(permission))
                        .filter(p -> channel.canSee(p.getUniqueId()))
                        .forEach(p -> adventure.player(p).sendMessage(message));

                adventure.console().sendMessage(message);
            } else {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> channel.canSee(p.getUniqueId()))
                        .forEach(p -> adventure.player(p).sendMessage(message));

                adventure.console().sendMessage(message);
            }
        };

        this.chatService = new ChatService(spigotAdapter);
        ServiceRegistry.getInstance().registerService(ChatService.class, this.chatService);

        this.chatService.registerChannel(new LocalChannel());

        logger.info("[Chat] Chat Local migrado para o Framework.");
    }

    @Override
    protected void onDisable() throws Exception {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }

        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.unregisterListener(RedisChannel.CHAT_CHANNEL));

        ServiceRegistry.getInstance().unregisterService(ChatService.class);
    }
}