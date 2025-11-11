package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.server.data.ServerInfo;
import com.realmmc.controller.modules.server.data.ServerInfoRepository;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StaffChatListener implements RedisMessageListener {

    private static final Logger LOGGER = Logger.getLogger(StaffChatListener.class.getName());
    private static final String STAFF_PERMISSION = "controller.helper";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ProxyServer proxyServer;
    private final ServerInfoRepository serverInfoRepository;
    private final PreferencesService preferencesService;
    private final Optional<SoundPlayer> soundPlayerOpt;

    public StaffChatListener() {
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.serverInfoRepository = new ServerInfoRepository();
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannel.STAFF_CHAT.getName().equals(channel)) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(message);

            String serverName = node.path("serverName").asText("unknown");
            String playerName = node.path("playerName").asText("Unknown");
            String formattedName = node.path("formattedName").asText(playerName);
            String textMessage = node.path("message").asText("");

            String serverDisplayName = serverInfoRepository.findByName(serverName)
                    .map(ServerInfo::getDisplayName)
                    .orElse(serverName);

            String format = "<dark_purple>[Staff] <dark_gray>[<server>] <reset><formattedName><dark_purple>: <white><message>";

            Component formattedMessage = miniMessage.deserialize(format,
                    Placeholder.component("server", Component.text(serverDisplayName)),
                    Placeholder.component("formattedName", miniMessage.deserialize(formattedName)),
                    Placeholder.component("message", Component.text(textMessage))
            );

            Component messageWithClick = formattedMessage.clickEvent(ClickEvent.suggestCommand("/btp " + playerName));

            proxyServer.getAllPlayers().stream()
                    .filter(player -> player.hasPermission(STAFF_PERMISSION))
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