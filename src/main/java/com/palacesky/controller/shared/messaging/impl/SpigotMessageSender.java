package com.palacesky.controller.shared.messaging.impl;

import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.MessageSender;
import com.palacesky.controller.shared.messaging.MessageTranslator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;

public class SpigotMessageSender implements MessageSender {

    private final MessageTranslator translator;
    private final MiniMessage miniMessage;

    public SpigotMessageSender(MessageTranslator translator) {
        this.translator = translator;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void sendMessage(Object recipient, Message message) {
        if (!isValidRecipient(recipient)) return;
        String translated = translator.translate(message);
        ((Audience) recipient).sendMessage(miniMessage.deserialize(translated));
    }

    @Override
    public void sendMessage(Object recipient, MessageKey key) {
        sendMessage(recipient, Message.of(key));
    }

    @Override
    public void sendRawMessage(Object recipient, String text) {
        if (!isValidRecipient(recipient)) return;
        ((Audience) recipient).sendMessage(miniMessage.deserialize(text));
    }

    @Override
    public void broadcastMessage(Iterable<?> recipients, Message message) {
        String translated = translator.translate(message);
        Component component = miniMessage.deserialize(translated);
        for (Object recipient : recipients) {
            if (isValidRecipient(recipient)) {
                ((Audience) recipient).sendMessage(component);
            }
        }
    }

    @Override
    public void broadcastMessage(Iterable<?> recipients, MessageKey key) {
        broadcastMessage(recipients, Message.of(key));
    }


    @Override
    public void sendActionBar(Object recipient, Message message) {
        if (!isValidRecipient(recipient)) return;
        String translated = translator.translate(message);
        ((Audience) recipient).sendActionBar(miniMessage.deserialize(translated));
    }

    @Override
    public void sendTitle(Object recipient, Message titleMsg, Message subtitleMsg, Title.Times times) {
        if (!isValidRecipient(recipient)) return;
        String t = (titleMsg != null) ? translator.translate(titleMsg) : "";
        String s = (subtitleMsg != null) ? translator.translate(subtitleMsg) : "";
        sendTitle(recipient, t, s, times);
    }


    @Override
    public void sendActionBar(Object recipient, String text) {
        if (!isValidRecipient(recipient)) return;
        ((Audience) recipient).sendActionBar(miniMessage.deserialize(text != null ? text : ""));
    }

    @Override
    public void sendTitle(Object recipient, String title, String subtitle, Title.Times times) {
        if (!isValidRecipient(recipient)) return;
        Component tComp = (title != null) ? miniMessage.deserialize(title) : Component.empty();
        Component sComp = (subtitle != null) ? miniMessage.deserialize(subtitle) : Component.empty();
        Title t = Title.title(tComp, sComp, times);
        ((Audience) recipient).showTitle(t);
    }

    @Override
    public void showBossBar(Object recipient, BossBar bar) {
        if (!isValidRecipient(recipient)) return;
        ((Audience) recipient).showBossBar(bar);
    }

    @Override
    public void hideBossBar(Object recipient, BossBar bar) {
        if (!isValidRecipient(recipient)) return;
        ((Audience) recipient).hideBossBar(bar);
    }

    @Override
    public boolean isValidRecipient(Object recipient) {
        return recipient instanceof CommandSender;
    }

    @Override
    public MessageTranslator getTranslator() {
        return translator;
    }
}