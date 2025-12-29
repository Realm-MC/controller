package com.palacesky.controller.shared.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.RedisPublisher;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import com.palacesky.controller.shared.storage.redis.packet.ChatPacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatService implements RedisMessageListener {

    private final Map<String, ChatChannel> channels = new ConcurrentHashMap<>();

    private final List<ChatInterceptor> interceptors = new CopyOnWriteArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger = Logger.getLogger("ChatService");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final PlatformChatAdapter adapter;

    public ChatService(PlatformChatAdapter adapter) {
        this.adapter = adapter;
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.CHAT_CHANNEL, this));
    }

    public void registerChannel(ChatChannel channel) {
        channels.put(channel.getId().toLowerCase(), channel);
    }

    /**
     * Regista um interceptor (ex: Plugin de Punições).
     */
    public void registerInterceptor(ChatInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    /**
     * Envia uma mensagem (Método atualizado com UUID).
     */
    public void sendMessage(String channelId, UUID senderUuid, String senderName, String senderDisplayName, String message, String serverOrigin) {
        ChatChannel channel = channels.get(channelId.toLowerCase());
        if (channel == null) return;

        for (ChatInterceptor interceptor : interceptors) {
            if (!interceptor.canSend(channelId, senderUuid, message)) {
                return;
            }
        }

        if (channel.isGlobal()) {
            ChatPacket packet = ChatPacket.builder()
                    .channelId(channel.getId())
                    .serverOrigin(serverOrigin)
                    .senderUuid(senderUuid)
                    .senderName(senderName)
                    .senderDisplayName(senderDisplayName)
                    .message(message)
                    .permissionRequired(channel.getPermission())
                    .build();
            RedisPublisher.publish(packet);
        } else {
            distributeLocal(channel, senderName, senderDisplayName, message, serverOrigin);
        }
    }

    @Override
    public void onMessage(String channelName, String messageJson) {
        if (!RedisChannel.CHAT_CHANNEL.getName().equals(channelName)) return;

        try {
            ChatPacket packet = mapper.readValue(messageJson, ChatPacket.class);
            ChatChannel channel = channels.get(packet.getChannelId().toLowerCase());

            if (channel != null) {
                distributeLocal(channel, packet.getSenderName(), packet.getSenderDisplayName(), packet.getMessage(), packet.getServerOrigin());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao processar ChatPacket", e);
        }
    }

    private void distributeLocal(ChatChannel channel, String senderName, String displayName, String message, String serverOrigin) {
        String format = channel.getFormat();
        String formatted = format
                .replace("{player}", displayName)
                .replace("{sender}", senderName)
                .replace("{message}", message)
                .replace("{server}", serverOrigin);

        Component component = miniMessage.deserialize(formatted);

        if (formatted.contains(displayName) && channel.getId().equals("staff")) {
            component = component.clickEvent(ClickEvent.suggestCommand("/btp " + senderName));
        }

        adapter.broadcast(component, channel.getPermission(), channel);
    }

    public interface PlatformChatAdapter {
        void broadcast(Component message, String permission, ChatChannel channel);
    }
}