package com.palacesky.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.modules.server.data.ServerInfo;
import com.palacesky.controller.modules.server.data.ServerInfoRepository;
import com.palacesky.controller.shared.annotations.Listeners;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.preferences.PreferencesService;
import com.palacesky.controller.shared.role.RoleType;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Listeners
public class StaffChatListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(StaffChatListener.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ProxyServer proxyServer;
    private final ServerInfoRepository serverInfoRepository;
    private final PreferencesService preferencesService;
    private final RoleService roleService;
    private final Optional<SoundPlayer> soundPlayerOpt;

    public StaffChatListener() {
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.serverInfoRepository = new ServerInfoRepository();
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.STAFF_CHAT.getName().equals(channel)) return;

        try {
            JsonNode node = objectMapper.readTree(message);
            String serverName = node.path("serverName").asText("unknown");
            String playerName = node.path("playerName").asText("Unknown");
            String formattedName = node.path("formattedName").asText(playerName);
            String textMessage = node.path("message").asText("");

            String serverDisplayName = serverInfoRepository.findByName(serverName)
                    .map(ServerInfo::getDisplayName)
                    .orElse(serverName);

            String format = Messages.translate(Message.of(MessageKey.STAFFCHAT_FORMAT)
                    .with("server", serverDisplayName)
                    .with("formatted_name", formattedName)
                    .with("message", textMessage));

            Component formattedComponent = miniMessage.deserialize(format);
            Component messageWithClick = formattedComponent.clickEvent(ClickEvent.suggestCommand("/btp " + playerName));

            proxyServer.getAllPlayers().stream()
                    .filter(player -> roleService.getSessionDataFromCache(player.getUniqueId())
                            .map(session -> session.getPrimaryRole().getType() == RoleType.STAFF)
                            .orElse(false))
                    .filter(player -> preferencesService.getCachedStaffChatEnabled(player.getUniqueId()).orElse(true))
                    .forEach(staff -> {
                        soundPlayerOpt.ifPresent(sp -> sp.playSound(staff, SoundKeys.NOTIFICATION));
                        staff.sendMessage(messageWithClick);
                    });

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao processar mensagem do StaffChat (Redis)", e);
        }
    }
}