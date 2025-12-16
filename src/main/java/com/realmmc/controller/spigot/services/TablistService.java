package com.realmmc.controller.spigot.services;

import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Locale;

public class TablistService implements Listener {

    private final Main plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private int animationIndex = 0;
    private static final int TOTAL_STATES = 3;

    public TablistService(Main plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAnimation, 0L, 200L);
    }

    private void tickAnimation() {
        animationIndex++;
        if (animationIndex >= TOTAL_STATES) {
            animationIndex = 0;
        }
        updateAll();
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTablist(player);
        }
    }

    private void sendTablist(Player player) {
        Locale locale = Messages.determineLocale(player);
        String headerString = Messages.translate(Message.of(MessageKey.TABLIST_HEADER), locale);
        String footerString;

        switch (animationIndex) {
            case 0:
                footerString = Messages.translate(Message.of(MessageKey.TABLIST_FOOTER_SITE), locale);
                break;
            case 1:
                footerString = Messages.translate(Message.of(MessageKey.TABLIST_FOOTER_STORE), locale);
                break;
            case 2:
                footerString = Messages.translate(Message.of(MessageKey.TABLIST_FOOTER_DISCORD), locale);
                break;
            default:
                footerString = Messages.translate(Message.of(MessageKey.TABLIST_FOOTER_SITE), locale);
                break;
        }

        player.sendPlayerListHeaderAndFooter(mm.deserialize(headerString), mm.deserialize(footerString));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendTablist(event.getPlayer());
    }
}