package com.palacesky.controller.modules.chat;

import com.palacesky.controller.core.modules.AbstractCoreModule;
import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.chat.channels.StaffChannel;
import com.palacesky.controller.shared.chat.ChatService;
import com.palacesky.controller.shared.preferences.PreferencesService;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class StaffChatModule extends AbstractCoreModule {

    private ChatService chatService;

    public StaffChatModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() { return "StaffChat"; }

    @Override
    public String getVersion() { return "2.0.0"; }

    @Override
    public String getDescription() { return "Módulo de Chat Global (Framework v2)"; }

    @Override
    public String[] getDependencies() { return new String[]{"Database", "Profile", "Preferences"}; }

    @Override
    public int getPriority() { return 60; }

    @Override
    protected void onEnable() throws Exception {
        logger.info("[Chat] Inicializando Framework de Chat no Proxy...");

        ProxyServer server = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        Optional<SoundPlayer> soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        PreferencesService prefs = ServiceRegistry.getInstance().requireService(PreferencesService.class);

        ChatService.PlatformChatAdapter proxyAdapter = (message, permission, channel) -> {
            for (Player p : server.getAllPlayers()) {
                if (permission != null && !p.hasPermission(permission)) continue;

                if (channel.getId().equals("staff")) {
                    boolean enabled = prefs.getCachedStaffChatEnabled(p.getUniqueId()).orElse(true);
                    if (!enabled) continue;
                }

                if (!channel.canSee(p.getUniqueId())) continue;

                if (channel.getId().equals("staff")) {
                    soundPlayer.ifPresent(sp -> sp.playSound(p, SoundKeys.NOTIFICATION));
                }
                p.sendMessage(message);
            }
        };

        this.chatService = new ChatService(proxyAdapter);
        ServiceRegistry.getInstance().registerService(ChatService.class, this.chatService);

        this.chatService.registerChannel(new StaffChannel());

        logger.info("[Chat] Serviço de Chat e Canal 'staff' registrados.");
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.unregisterListener(RedisChannel.CHAT_CHANNEL));

        ServiceRegistry.getInstance().unregisterService(ChatService.class);
    }
}