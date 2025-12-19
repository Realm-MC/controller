package com.palacesky.controller.shared.messaging;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;

public interface MessageSender {
    void sendMessage(Object recipient, Message message);
    void sendMessage(Object recipient, MessageKey key);
    void sendRawMessage(Object recipient, String text);
    void broadcastMessage(Iterable<?> recipients, Message message);
    void broadcastMessage(Iterable<?> recipients, MessageKey key);

    void sendActionBar(Object recipient, Message message);
    void sendTitle(Object recipient, Message title, Message subtitle, Title.Times times);

    void sendActionBar(Object recipient, String text);
    void sendTitle(Object recipient, String title, String subtitle, Title.Times times);

    void showBossBar(Object recipient, BossBar bar);
    void hideBossBar(Object recipient, BossBar bar);

    boolean isValidRecipient(Object recipient);
    MessageTranslator getTranslator();
}