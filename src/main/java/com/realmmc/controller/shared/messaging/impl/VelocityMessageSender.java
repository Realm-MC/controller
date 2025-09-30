package com.realmmc.controller.shared.messaging.impl;

import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.MessageSender;
import com.realmmc.controller.shared.messaging.MessageTranslator;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class VelocityMessageSender implements MessageSender {

    private final MessageTranslator translator;
    private final MiniMessage miniMessage;

    public VelocityMessageSender(MessageTranslator translator) {
        this.translator = translator;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void sendMessage(Object recipient, Message message) {
        if (!isValidRecipient(recipient)) {
            return;
        }

        String translatedMessage = translator.translate(message);
        Component component = miniMessage.deserialize(translatedMessage);

        if (recipient instanceof CommandSource) {
            ((CommandSource) recipient).sendMessage(component);
        }
    }

    @Override
    public void sendMessage(Object recipient, MessageKey key) {
        sendMessage(recipient, Message.of(key));
    }

    @Override
    public void sendRawMessage(Object recipient, String text) {
        if (!isValidRecipient(recipient)) {
            return;
        }

        Component component = miniMessage.deserialize(text);

        if (recipient instanceof CommandSource) {
            ((CommandSource) recipient).sendMessage(component);
        }
    }

    @Override
    public void broadcastMessage(Iterable<?> recipients, Message message) {
        String translatedMessage = translator.translate(message);
        Component component = miniMessage.deserialize(translatedMessage);

        for (Object recipient : recipients) {
            if (isValidRecipient(recipient) && recipient instanceof CommandSource) {
                ((CommandSource) recipient).sendMessage(component);
            }
        }
    }

    @Override
    public void broadcastMessage(Iterable<?> recipients, MessageKey key) {
        broadcastMessage(recipients, Message.of(key));
    }

    @Override
    public boolean isValidRecipient(Object recipient) {
        return recipient instanceof CommandSource;
    }

    @Override
    public MessageTranslator getTranslator() {
        return translator;
    }
}