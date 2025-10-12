package com.realmmc.controller.proxy.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisMessageListener;
import com.realmmc.controller.shared.storage.redis.RedisSubscriber;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import java.time.Duration;

@Listeners
public class GlobalAnnouncementListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final RedisSubscriber subscriber;

    public GlobalAnnouncementListener() {
        this.subscriber = new RedisSubscriber();
        startListening();
    }

    private void startListening() {
        subscriber.registerListener(RedisChannel.GLOBAL_ANNOUNCEMENT, (channel, message) -> {
            try {
                JsonNode node = mapper.readTree(message);
                String type = node.path("type").asText();

                if ("ROLE_ANNOUNCEMENT".equals(type)) {
                    String playerNameLegacy = node.path("playerName").asText();
                    String roleNameLegacy = node.path("roleName").asText();

                    String playerNameMm = playerNameLegacy.replace('&', '§').replace('§', '<');
                    String roleNameMm = roleNameLegacy.replace('&', '§').replace('§', '<');

                    Component title = mm.deserialize(playerNameMm + " <gray>tornou-se");
                    Component subtitle = mm.deserialize(roleNameMm);

                    Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500));
                    Title fullTitle = Title.title(title, subtitle, times);

                    Sound sound = Sound.sound(
                            net.kyori.adventure.key.Key.key("entity.player.levelup"),
                            Sound.Source.MASTER, 1f, 1.5f
                    );

                    Proxy.getInstance().getServer().getAllPlayers().forEach(player -> {
                        player.showTitle(fullTitle);
                        player.playSound(sound);
                    });
                }
            } catch (Exception e) {
            }
        });
        subscriber.start();
    }
}